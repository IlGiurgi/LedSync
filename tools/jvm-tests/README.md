Desktop JVM checks for the audio analysis and effects (no device needed). They are plain `main` programs.
Compile against an android.jar for the few framework types, e.g.:

    javac -cp android-34.jar -d out app/src/main/java/com/ledsync/phone/{AudioAnalyzer,DirectEffects,Tuning}.java tools/jvm-tests/AllEffectsTest.java
    java -cp out AllEffectsTest

`Matrix2DTest` renders every native 2D matrix effect on an 11x10 word clock into a PNG (argument: output file).

`YeelightModesTest` runs a fake Yeelight on 127.0.0.1:55443 (compile it with `YeelightLink.java`).

`LightChainTest` joins strip + lamp + strip into one strip and checks the effect travels across them (argument: PNG timeline).

`DropTest` plays a synthetic dance track with a build-up and a drop (plus a slow fade-in, a pause and a
steady 3-minute mix that must give no drops); compile it with `DropDetector.java` and `BeatTracker.java`.

`DelayCalibratorTest` simulates the automatic Bluetooth delay measurement (delays 0-700 ms, noise, echo, music):

    javac -cp android-34.jar -d out -sourcepath app/src/main/java app/src/main/java/com/ledsync/phone/DelayCalibrator.java tools/jvm-tests/DelayCalibrator*.java
    java -cp out:android-34.jar DelayCalibratorTest
