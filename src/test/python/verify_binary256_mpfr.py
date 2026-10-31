"""使用 gmpy2 的 MPFR 内核对照原始编码，需要先编译 Java 测试类。"""

from pathlib import Path
import os
import random
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "target" / "verification-python"))
import gmpy2 as g

CONTEXT = g.context(precision=237, emin=-262377, emax=262144,
                    subnormalize=True, round=g.RoundToNearest)
MASK = (1 << 236) - 1
INF = 0x7FFFF << 236
NAN = INF | (1 << 235)
SIGN = 1 << 255


def decode(bits):
    negative = bool(bits & SIGN)
    exponent = (bits >> 236) & 0x7FFFF
    fraction = bits & MASK
    if exponent == 0x7FFFF:
        return g.mpfr("nan" if fraction else "-inf" if negative else "inf")
    if exponent == 0 and fraction == 0:
        return g.mpfr("-0.0" if negative else "0.0")
    magnitude = fraction if exponent == 0 else fraction | (1 << 236)
    scale = (exponent or 1) - 262143 - 236
    value = g.mpfr(magnitude)
    value = g.mul_2exp(value, scale) if scale >= 0 else g.div_2exp(value, -scale)
    return -value if negative else value


def encode(value, precision=237, emin=-262142, emax=262143, exponent_bits=19):
    fraction_bits = precision - 1
    infinity = ((1 << exponent_bits) - 1) << fraction_bits
    if g.is_nan(value):
        return infinity | (1 << (fraction_bits - 1))
    sign = int(g.is_signed(value)) << (fraction_bits + exponent_bits)
    if g.is_infinite(value):
        return sign | infinity
    if value == 0:
        return sign
    magnitude, scale = value.as_mantissa_exp()
    magnitude = abs(int(magnitude))
    scale = int(scale)
    exponent = magnitude.bit_length() - 1 + scale
    quantum = max(exponent - fraction_bits, emin - fraction_bits)
    difference = scale - quantum
    if difference < 0:
        assert magnitude & ((1 << -difference) - 1) == 0
        magnitude >>= -difference
    else:
        magnitude <<= difference
    encoded = 0 if exponent < emin else exponent + emax
    return sign | (encoded << fraction_bits) | (magnitude & ((1 << fraction_bits) - 1))


def integral(value, width):
    if g.is_nan(value):
        return 0
    minimum, maximum = -(1 << (width - 1)), (1 << (width - 1)) - 1
    if g.is_infinite(value):
        result = minimum if g.is_signed(value) else maximum
    else:
        numerator, denominator = value.as_integer_ratio()
        result = int(abs(numerator) // denominator)
        if numerator < 0:
            result = -result
        result = max(minimum, min(maximum, result))
    return result & ((1 << width) - 1)


def expected(operation, a, b, c):
    if operation == "float":
        with g.ieee(32):
            return encode(+a, 24, -126, 127, 8)
    if operation == "double":
        with g.ieee(64):
            return encode(+a, 53, -1022, 1023, 11)
    if operation in ("int", "long"):
        return integral(a, 32 if operation == "int" else 64)
    operations = {
        "add": lambda: a + b,
        "sub": lambda: a - b,
        "mul": lambda: a * b,
        "div": lambda: a / b,
        "fma": lambda: g.fma(a, b, c),
        "rem": lambda: g.fmod(a, b),
        "ieee": lambda: g.remainder(a, b),
        "sqrt": lambda: g.sqrt(a),
    }
    return encode(operations[operation]())


def verify():
    random_source = random.Random(0x2564D504652)
    one = 262143 << 236
    minimum_normal = 1 << 236
    maximum = ((0x7FFFF - 1) << 236) | MASK
    special = [0, SIGN, 1, SIGN | 1, minimum_normal - 1, minimum_normal,
               one, one + 1, one - 1, maximum, SIGN | maximum,
               INF, SIGN | INF, NAN, SIGN | INF | 1]
    vectors = [(a, b, special[(i * 3 + j) % len(special)])
               for i, a in enumerate(special) for j, b in enumerate(special)]
    for i in range(250):
        values = []
        for _ in range(3):
            exponent = random_source.choice([0, 1, 2, 262143, 0x7FFFD, 0x7FFFE]) if i < 100 else random_source.randrange(0x7FFFF)
            values.append((random_source.getrandbits(1) << 255) | (exponent << 236) | random_source.getrandbits(236))
        vectors.append(tuple(values))

    requests, expectations = [], []
    operations = ("add", "sub", "mul", "div", "fma", "rem", "ieee", "sqrt", "float", "double", "int", "long")
    with CONTEXT:
        for raw_a, raw_b, raw_c in vectors:
            a, b, c = decode(raw_a), decode(raw_b), decode(raw_c)
            for operation in operations:
                request = f"{operation} {raw_a:064x} {raw_b:064x} {raw_c:064x}"
                requests.append(request)
                expectations.append(expected(operation, a, b, c))

    classpath = os.pathsep.join(str(ROOT / "target" / path) for path in ("test-classes", "classes"))
    process = subprocess.run(["java", "-cp", classpath, "com.inf.long2.binaries.Binary256MpfrProbe"],
                             input="\n".join(requests) + "\n", text=True, capture_output=True, check=True, cwd=ROOT)
    outputs = process.stdout.splitlines()
    assert len(outputs) == len(expectations), process.stderr
    failures = []
    for request, result, target in zip(requests, outputs, expectations):
        if int(result, 16) != target:
            failures.append(f"{request}\nactual={result}\nexpected={target:x}")
    if failures:
        raise AssertionError("\n".join(failures[:10]) + f"\n{len(failures)} failures")
    print(f"MPFR {g.mpfr_version()}: {len(requests)} raw-bit comparisons passed")


if __name__ == "__main__":
    verify()
