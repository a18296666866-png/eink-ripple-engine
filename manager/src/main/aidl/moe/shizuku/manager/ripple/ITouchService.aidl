package moe.shizuku.manager.ripple;
import moe.shizuku.manager.ripple.ITouchCallback;
interface ITouchService {
    void destroy() = 16777114;
    void startListening() = 1;
    void stopListening() = 2;
    void setSpeed(int speed) = 3;
    void setHanvonSpeed(int speed) = 4;
    void setWaterfallMode(boolean isWaterfall) = 5;
    void setRotation(int rotation) = 6;
    void setTargetPackages(in String[] pkgNames) = 7;
    boolean isListening() = 8;
    void registerCallback(ITouchCallback cb) = 9;
    void setReverseAnimation(boolean reverse) = 10;
}
