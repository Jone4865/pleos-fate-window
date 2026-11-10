package com.fatewindow.app.game

import kotlin.random.Random

/** 게임 모드. 룰렛 엔진은 같고 당첨 후 미션만 다르다. */
enum class GameMode(val title: String, val desc: String) {
    LUCKY_DRAW("복불복", "걸린 사람이 쏜다"),
    TRUTH_OR_DARE("진실 혹은 도전", "질문에 답하거나 미션 수행"),
    PENALTY("벌칙 결정기", "미니게임 패자에게 벌칙"),
    SPOTLIGHT_TALK("스포트라이트 토크", "지목된 사람이 이야기 주인공"),
}

/** 복불복 내기 항목 (사용자가 칩으로 고름) */
data class Stake(val id: String, val label: String, val mission: String)

/** 당첨자에게 보여줄 미션 카드 */
data class Mission(val tag: String, val lines: List<Pair<String?, String>>) {
    /** TTS 로 읽을 문장 */
    val speech: String get() = lines.joinToString(". ") { (k, v) -> if (k != null) "$k, $v" else v }
}

object Missions {

    val stakes = listOf(
        Stake("snack", "간식", "다음 휴게소 간식은 당신이 쏩니다"),
        Stake("coffee", "커피", "모두의 커피는 당신 몫"),
        Stake("drive", "다음 운전", "다음 구간 운전은 당신이 맡아요"),
        Stake("parking", "주차비", "오늘 주차비는 당신이 계산해요"),
        Stake("luggage", "짐 나르기", "트렁크 짐은 전부 당신이 옮겨요"),
        Stake("dj", "DJ", "다음 30분 선곡 담당, 취향 존중 필수"),
        Stake("food", "맛집 검색", "다음 식사 장소 찾기는 당신 몫"),
        Stake("clean", "차 정리", "내릴 때 차 안 정리 담당"),
    )

    private val truths = listOf(
        "최근 가장 창피했던 순간은?",
        "이 차에 탄 사람 중 첫인상과 가장 달랐던 사람은?",
        "요즘 가장 큰 고민 하나",
        "가장 최근에 운 이유는?",
        "여기 있는 사람에게 서운했던 적 있다면?",
        "아무도 모르는 나만의 특기는?",
        "최근에 한 작은 거짓말 하나",
        "휴대폰 최근 검색어 하나 공개",
        "다시 태어나면 해보고 싶은 일은?",
        "이 중 무인도에 같이 갈 한 명과 이유",
        "올해 가장 잘한 소비와 가장 후회한 소비",
        "어릴 때 장래희망은?",
    )

    private val dares = listOf(
        "10초간 아무나 성대모사",
        "옆 사람 칭찬 세 가지",
        "좋아하는 노래 후렴 부르기",
        "사진첩 세 번째 사진 공개",
        "1분 동안 아재개그 연속 세 개",
        "다음 라운드까지 모두에게 존댓말",
        "각 탑승자 성대모사 한 마디씩",
        "가족이나 친구에게 고맙다는 문자 보내기",
        "가장 웃긴 표정으로 단체 셀카",
        "랩으로 자기소개",
        "운전자에게 감사 인사 30초",
        "오늘 여행 한 줄 CF 멘트 만들기",
    )

    private val penalties = listOf(
        "10초간 성대모사",
        "애교 세 종 세트",
        "다음 라운드까지 말끝마다 '냥'",
        "원하는 사람에게 어깨 안마 30초",
        "엽기 표정 셀카 찍어서 단톡방에 올리기",
        "'간장공장 공장장' 세 번 빠르게",
        "다음 휴게소까지 리액션 담당",
        "1분간 웃지 않기, 나머지는 웃기기",
        "노래 한 소절 고음으로",
        "자기 별명 세 개 짓고 발표",
        "모두에게 하이파이브 돌리기",
        "다음 라운드 진행자 멘트 맡기",
    )

    private val talks = listOf(
        "지금까지 가장 행복했던 여행",
        "올해 스스로에게 칭찬하고 싶은 일",
        "여기 있는 사람에게 고마웠던 순간",
        "요즘 푹 빠져 있는 것",
        "10년 뒤 나에게 한마디",
        "처음 운전했던 날 이야기",
        "가장 기억에 남는 차 안의 순간",
        "다음에 같이 가고 싶은 곳",
        "최근 가장 크게 웃었던 일",
        "나만 아는 맛집 하나",
        "어린 시절 가장 좋아했던 놀이",
        "요즘 나를 버티게 하는 것",
    )

    fun pick(mode: GameMode, enabledStakes: Set<String>, rng: Random = Random.Default): Mission = when (mode) {
        GameMode.LUCKY_DRAW -> {
            val s = stakes.filter { it.id in enabledStakes }.ifEmpty { stakes }.random(rng)
            Mission(s.label, listOf(null to s.mission))
        }
        GameMode.TRUTH_OR_DARE -> Mission("진실 혹은 도전", listOf("진실" to truths.random(rng), "도전" to dares.random(rng)))
        GameMode.PENALTY -> Mission("벌칙", listOf(null to penalties.random(rng)))
        GameMode.SPOTLIGHT_TALK -> Mission("오늘의 주인공", listOf("이야기 주제" to talks.random(rng)))
    }

    /** 직전 미션과 다른 것을 고른다 (다시 뽑기용) */
    fun pickDifferent(mode: GameMode, enabledStakes: Set<String>, prev: Mission?, rng: Random = Random.Default): Mission {
        repeat(8) {
            val m = pick(mode, enabledStakes, rng)
            if (m != prev) return m
        }
        return pick(mode, enabledStakes, rng)
    }

    fun poolHint(mode: GameMode): String = when (mode) {
        GameMode.LUCKY_DRAW -> "걸린 사람이 할 일"
        GameMode.TRUTH_OR_DARE -> "진실 ${truths.size}개 · 도전 ${dares.size}개 중 랜덤 출제"
        GameMode.PENALTY -> "벌칙 ${penalties.size}개 중 랜덤"
        GameMode.SPOTLIGHT_TALK -> "가족·커플 대화 주제 ${talks.size}개 중 랜덤"
    }
}
