package moe.shizuku.manager.ripple;
interface ITouchCallback {
    void onPageEvent(String source, String direction, String rawCode,
                     int startX, int startY, int endX, int endY,
                     int durationMs, boolean rippleSent);
    void onLog(String message);
}
