/**
 * 경기 간격(일수) → 세로 여백(px) 환산.
 *
 * 왜 선형(px = days × k)이 아니라 로그 압축 스케일인가:
 *   실제 간격 분포는 3일과 14일이 공존한다(밀집 vs 인터내셔널 브레이크). 선형이면
 *   14일 공백이 3일 간격의 4.7배 높이를 먹어 화면을 잠식하고, 정작 보여주려는
 *   "3-4일 밀집"이 상대적으로 뭉개진다. 사람은 간격의 절대 길이보다 "붙었나/떴나"의
 *   비율로 읽으므로(로그적 지각, Weber–Fechner), 큰 값의 증가폭을 둔화시키는 로그
 *   스케일이 맞다. 여기에 [MIN,MAX] 클램프를 걸어 아무리 긴 공백도 한 화면을 넘기지
 *   않게 한다. 결과: 14일이 3일의 약 2배 높이 → "확실히 더 길지만 압도하지 않음".
 */

export const GAP_MIN_PX = 14;
export const GAP_MAX_PX = 88;

export function gapToPx(days: number): number {
  if (days <= 0) return 0;
  const px = 12 + 16 * Math.log2(days);
  return Math.round(Math.min(GAP_MAX_PX, Math.max(GAP_MIN_PX, px)));
}

/** "14일 휴식"(장기) 또는 "3일"(단기). */
export function gapLabel(days: number): string {
  return days >= 10 ? `${days}일 휴식` : `${days}일`;
}
