Desktop JVM checks for the audio analysis and effects (no device needed). They are plain `main` programs.
Compile against an android.jar for the few framework types, e.g.:

    javac -cp android-34.jar -d out app/src/main/java/com/ledsync/phone/{AudioAnalyzer,DirectEffects,Tuning}.java tools/jvm-tests/AllEffectsTest.java
    java -cp out AllEffectsTest

`YeelightModesTest` runs a fake Yeelight on 127.0.0.1:55443 (compile it with `YeelightLink.java`).

`DelayCalibratorTest` simulates the automatic Bluetooth delay measurement (delays 0-700 ms, noise, echo, music):

    javac -cp android-34.jar -d out -sourcepath app/src/main/java app/src/main/java/com/ledsync/phone/DelayCalibrator.java tools/jvm-tests/DelayCalibrator*.java
    java -cp out:android-34.jar DelayCalibratorTest
