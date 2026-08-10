import { dirname } from "path";
import { fileURLToPath } from "url";
import { FlatCompat } from "@eslint/eslintrc";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const compat = new FlatCompat({
  baseDirectory: __dirname,
});

const eslintConfig = [
  // 우리가 쓰지 않은 파일은 린트하지 않는다. `.next/` 는 빌드 산출물이고
  // `next-env.d.ts` 는 Next 가 생성한다 — 고칠 수 없는 파일의 오류가 쌓이면
  // "린트 통과"가 아무 뜻도 없는 상태가 된다.
  { ignores: [".next/**", "next-env.d.ts"] },
  ...compat.extends("next/core-web-vitals", "next/typescript"),
];

export default eslintConfig;
