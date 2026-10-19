package com.lightxin.navigation

import android.net.Uri

object Routes {
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val HOME = "home"

    // Checkin
    const val CHECKIN_LIST = "checkin/list"
    const val CHECKIN_DETAIL = "checkin/detail/{taskDateId}"
    const val CHECKIN_HISTORY = "checkin/history"
    fun checkinDetail(taskDateId: String) = "checkin/detail/$taskDateId"

    // Holiday
    const val HOLIDAY_REGISTER = "holiday/register/{holidayId}"
    fun holidayRegister(holidayId: String) = "holiday/register/$holidayId"

    // Schedule
    const val SPECIAL_SECTIONS = "schedule/special-sections"

    // Running
    const val RUNNING_HOME = "running/home"
    const val RUNNING_ACTIVE = "running/active"
    const val RUNNING_SIM = "running/sim"
    const val RUNNING_RESULT = "running/result"
    const val RUNNING_ROUTE_SETTINGS = "running/route/settings"
    const val RUNNING_ROUTE_RECORD = "running/route/record"
    const val RUNNING_ROUTE_LIST = "running/route/list"
    const val RUNNING_ROUTE_DETAIL = "running/route/detail/{templateId}"
    fun runningRouteDetail(templateId: String) = "running/route/detail/$templateId"

    // Running - 大二俱乐部 / 锻炼考勤
    const val RUNNING_CLUB_DETAIL = "running/club"
    const val RUNNING_EXERCISE_CHECK = "running/exercise/{autoId}/{memberId}"
    fun runningExerciseCheck(autoId: String, memberId: String) =
        "running/exercise/${Uri.encode(autoId)}/${Uri.encode(memberId)}"

    // Labor
    const val LABOR_SUMMARY = "labor/summary"

    // AI Class
    const val AICLASS_HOME = "aiclass/home"
    const val AICLASS_SCAN = "aiclass/scan"
    const val AICLASS_DETAIL = "aiclass/detail/{classId}"
    fun aiClassDetail(classId: String) = "aiclass/detail/${Uri.encode(classId)}"

    // More Features
    const val MORE_FEATURES = "more"

    // Exam
    const val EXAM_SCORES = "exam/scores"

    // Credit
    const val CREDIT_OVERVIEW = "credit/overview"

    // AI Homework
    const val AICLASS_HOMEWORK_DETAIL = "aiclass/homework/{cwId}/{teachClassId}"
    fun aiClassHomeworkDetail(cwId: String, teachClassId: String) =
        "aiclass/homework/${Uri.encode(cwId)}/${Uri.encode(teachClassId)}"

    // About
    const val ABOUT = "about"

    /**
     * 通知点击允许直达的路由。
     *
     * MainActivity 是 exported 的，任何应用都能 startActivity 带一个 `notification_route` extra，
     * 之前拿到什么就 navigate 什么：口令门禁挡住的「模拟提交」屏、扫码相机都能被外部直接拉起，
     * 塞一个不存在的路由还能崩在 IllegalArgumentException 上。
     * 只认这份名单、且只认精确匹配 —— 带参数的路由（如 checkin/detail/{id}）本来就不在名单里。
     */
    private val NOTIFICATION_DESTINATIONS = setOf(AICLASS_HOME, RUNNING_ACTIVE)

    fun notificationRouteOrNull(raw: String?): String? = raw?.takeIf { it in NOTIFICATION_DESTINATIONS }
}
