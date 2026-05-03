package com.smily.bot.bot;

public final class CallbackData {
    public static final String MENU_MAIN = "menu:main";
    public static final String MENU_FOOD = "menu:food";
    public static final String MENU_PIPISA = "menu:pipisa";
    public static final String FOOD_EAT = "food:eat";
    public static final String FOOD_RATING = "food:rating";
    public static final String FOOD_RATING_DAY = "food:rating:day";
    public static final String FOOD_RATING_MONTH = "food:rating:month";
    public static final String FOOD_RATING_ALL = "food:rating:all";
    public static final String FOOD_CHALLENGES = "food:challenges";
    public static final String PIPISA_MEASURE = "pipisa:measure";
    public static final String PIPISA_RATING = "pipisa:rating";
    public static final String PIPISA_RATING_DAY = "pipisa:rating:day";
    public static final String PIPISA_RATING_MONTH = "pipisa:rating:month";
    public static final String PIPISA_RATING_ALL = "pipisa:rating:all";

    public static final String ADMIN_MENU = "admin:menu";
    public static final String ADMIN_STATS = "admin:stats";
    public static final String ADMIN_USERS = "admin:users";
    public static final String ADMIN_USERS_PAGE_PREFIX = "admin:users:page:";
    public static final String ADMIN_KEYWORDS = "admin:keywords";
    public static final String ADMIN_KEYWORD_ADD_PROMPT = "admin:keyword:add";
    public static final String ADMIN_COUNTERS = "admin:counters";
    public static final String ADMIN_COUNTER_SET_PROMPT = "admin:counter:set";
    public static final String ADMIN_COUNTER_ADD_PROMPT = "admin:counter:add";
    public static final String ADMIN_LIMITS = "admin:limits";
    public static final String ADMIN_LIMITS_RESET_ALL = "admin:limits:reset_all";
    public static final String ADMIN_LIMITS_RESET_USER_PROMPT = "admin:limits:reset_user";

    private CallbackData() {
    }
}
