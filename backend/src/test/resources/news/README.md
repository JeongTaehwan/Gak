# 뉴스 피드 — RSS 표본과 분류기 비교 (2026-08-03 수집)

이 폴더는 **분석용 표본**이다. 프로덕션 코드가 읽지 않는다.
(`apifootball/`의 replay 파일과 성격이 다르다 — 저건 파이프라인이 실제로 읽는다.)

## 파일

| 파일 | 내용 |
| --- | --- |
| `raw-*.xml` | 각 피드의 원본 응답 4개 |
| `headlines.json` | 파싱 결과 (제목·링크·발행시각·guid) 137건 |
| `classified-keyword.json` | 키워드 분류기 v1/v2 출력 |
| `gold-and-keyword.json` | 위 + **손으로 매긴 정답 라벨** |

## 수집한 소스 4개

| key | 소스 | URL | 건수 | 등급 |
| --- | --- | --- | ---: | --- |
| `bbc-football` | BBC Sport Football | `feeds.bbci.co.uk/sport/football/rss.xml` | 72 | MEDIA |
| `sky-football` | Sky Sports | `skysports.com/rss/12040` | 20 | MEDIA |
| `guardian-manutd` | Guardian 맨유 전담 | `theguardian.com/football/manchester-united/rss` | 20 | MEDIA |
| `men-manutd` | Manchester Evening News 맨유 | `manchestereveningnews.co.uk/all-about/manchester-united-fc/?service=rss` | 25 | MEDIA |

- **구단 공식(OFFICIAL) 소스는 없다.** 맨유 공식 RSS는 존재하지 않음(재확인).
  출처 등급 컬럼은 만들되 지금은 전부 MEDIA다.
- MEN은 robots.txt에서 `Crawl-delay: 10`을 요구한다. 수집 스크립트가 지킨다.
- User-Agent를 밝히고 요청한다.

## 정답 라벨 기준

`target = true` 는 **맨유 남자 1군**(구단 자체 / 현 소속 선수 / 감독 / 구단 운영)이
기사의 주된 주어일 때만. 즉:

- 여자팀(WSL)·유소년 → **false**
- "ex-/former Man United" 선수의 새 팀 소식 → **false**
- 프리미어리그 일반 칼럼, TV 편성 안내 → **false**

정답 분포: **참 37 / 거짓 100** (BBC 72건 중 참은 0건 — 프리시즌 주간이라 정상)

## 결과

### A. 팀 매칭

| | 정밀도 | 재현율 | 오탐 | 누락 |
| --- | ---: | ---: | ---: | ---: |
| v1 단순 문자열 포함 | 80.0% | 97.3% | 9 | 1 |
| v2 별칭 + 제외규칙 | **100.0%** | 97.3% | 0 | 1 |

v1이 틀린 9건의 **모양이 딱 두 가지**다:

1. 여자팀 (5건) — `Man Utd boss Skinner leaves role before WSL season`
2. 전 소속 선수 (4건) — `Ex-Man United star Casemiro endures nightmare start to Inter Miami`

v2는 규칙 두 줄로 둘 다 잡았다. 남은 누락 1건은
`A footballing deepfake: how Bruno Fernandes fell victim to...` —
**제목에 구단명이 아예 없다.** 선수 이름 사전이 있어야 잡힌다.

> ⚠️ v2의 제외 규칙은 이 표본을 **보고 나서** 썼다. 100%는 낙관적인 수치다.
> 다만 두 규칙 모두 특정 헤드라인이 아니라 일반적인 패턴을 겨냥한다.

### B. 카테고리 태깅 — 여기서 무너진다

팀 매칭이 맞은 36건 중 **25건만 정확 (69.4%)**. 틀리는 모양:

- `consider Camavinga and Berge as midfield overhaul` → OTHER (정답 TRANSFER)
  "consider"가 이적 어휘 목록에 없다. 넣으면 다른 데서 오탐이 난다.
- `MU think they can beat City to £100m Elliot Anderson` → MATCH 오탐
  **"beat"이 비유다.** 경기 얘기가 아니라 영입 경쟁 얘기다.
- SQUAD(선수단·훈련·복귀) 갈래를 거의 못 잡는다 — 8건 중 0건.
- 팀 매칭 밖에서도: `rain suspends DC Open final` → INJURY("suspend"),
  `Oshoala revives Nigeria's World Cup bid` → TRANSFER("bid")

### C. 분류 이전의 문제 — 어느 쪽 분류기도 못 고친다

- **중복** 5종 10건. 같은 제목이 한 피드 안에 두 번 온다. `guid`/`link`로 dedup 필수.
- **Sky 피드(12040)는 축구 전용이 아니다.** 20건 중 6건이 골프·크리켓·테니스·F1·경마.
  분류기로 거를 게 아니라 피드를 바꾸거나 소스별 스포츠 필터가 필요하다.

### 비용 (claude-haiku-4-5, $1/$5 per MTok · 20건씩 묶어 1요청)

| 하루 유입 | 캐시+배치API 적용 시 |
| --- | --- |
| 50건 | 연 **$1.13** |
| 100건 | 연 **$2.16** |
| 200건 | 연 **$4.23** |

**비용은 결정 요인이 아니다.** 연 몇 천 원이다.

## 재수집

`scripts/`에 넣지 않았다. 필요하면 `headlines.json`의 `source.url`을 보고 다시 받는다.
받기와 파싱을 한 번에 하고, 중간 산출물을 `/tmp`에 두지 않는다.
