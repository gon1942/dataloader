import argparse
from pathlib import Path

import cv2
import numpy as np


def odd(n: int) -> int:
    return n if n % 2 == 1 else n + 1


def solidify_dashed_lines(
    img_gray: np.ndarray,
    block_size: int,
    c: int,
    h_kernel: int,
    v_kernel: int,
    bridge_h: int,
    bridge_v: int,
    iterations: int,
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    bw_inv = cv2.adaptiveThreshold(
        img_gray,
        255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY_INV,
        odd(block_size),
        c,
    )

    # 1) 긴 가로/세로 성분만 먼저 추출(open): 텍스트 영향 최소화
    h_seed = cv2.morphologyEx(
        bw_inv,
        cv2.MORPH_OPEN,
        cv2.getStructuringElement(cv2.MORPH_RECT, (h_kernel, 1)),
        iterations=1,
    )
    v_seed = cv2.morphologyEx(
        bw_inv,
        cv2.MORPH_OPEN,
        cv2.getStructuringElement(cv2.MORPH_RECT, (1, v_kernel)),
        iterations=1,
    )

    # 2) 점선 연결(close): 선 성분에만 적용
    h = cv2.morphologyEx(
        h_seed,
        cv2.MORPH_CLOSE,
        cv2.getStructuringElement(cv2.MORPH_RECT, (max(1, bridge_h), 1)),
        iterations=iterations,
    )
    v = cv2.morphologyEx(
        v_seed,
        cv2.MORPH_CLOSE,
        cv2.getStructuringElement(cv2.MORPH_RECT, (1, max(1, bridge_v))),
        iterations=iterations,
    )
    lines = cv2.bitwise_or(h, v)

    out = img_gray.copy()
    out[lines > 0] = 0
    return out, lines, bw_inv


def main() -> None:
    ap = argparse.ArgumentParser(description="Solidify dashed table lines in a rendered page image.")
    ap.add_argument("--input", required=True, help="Input image path (png/jpg)")
    ap.add_argument("--output", required=True, help="Output image path")
    ap.add_argument("--block_size", type=int, default=31, help="Adaptive threshold block size (odd)")
    ap.add_argument("--c", type=int, default=15, help="Adaptive threshold C")
    ap.add_argument("--h_kernel", type=int, default=21, help="Horizontal line seed kernel width")
    ap.add_argument("--v_kernel", type=int, default=21, help="Vertical line seed kernel height")
    ap.add_argument("--bridge_h", type=int, default=9, help="Horizontal dash-bridge kernel width")
    ap.add_argument("--bridge_v", type=int, default=9, help="Vertical dash-bridge kernel height")
    ap.add_argument("--iterations", type=int, default=1, help="Morphology iterations")
    ap.add_argument(
        "--save_debug",
        action="store_true",
        help="Also save *_bw.png and *_lines.png next to output",
    )
    args = ap.parse_args()

    in_path = Path(args.input)
    out_path = Path(args.output)
    out_path.parent.mkdir(parents=True, exist_ok=True)

    if not in_path.exists():
        raise FileNotFoundError(f"Input not found: {in_path}")

    img_gray = cv2.imread(str(in_path), cv2.IMREAD_GRAYSCALE)
    if img_gray is None:
        raise RuntimeError(f"Failed to read image: {in_path}")

    out, lines, bw_inv = solidify_dashed_lines(
        img_gray=img_gray,
        block_size=max(3, odd(args.block_size)),
        c=args.c,
        h_kernel=max(1, args.h_kernel),
        v_kernel=max(1, args.v_kernel),
        bridge_h=max(1, args.bridge_h),
        bridge_v=max(1, args.bridge_v),
        iterations=max(1, args.iterations),
    )

    cv2.imwrite(str(out_path), out)
    print(f"saved: {out_path}")

    if args.save_debug:
        bw_path = out_path.with_name(f"{out_path.stem}_bw{out_path.suffix}")
        lines_path = out_path.with_name(f"{out_path.stem}_lines{out_path.suffix}")
        cv2.imwrite(str(bw_path), bw_inv)
        cv2.imwrite(str(lines_path), lines)
        print(f"saved: {bw_path}")
        print(f"saved: {lines_path}")


if __name__ == "__main__":
    main()
