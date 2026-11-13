Desktop JVM checks for the audio analysis and effects (no device needed). They are plain `main` programs.
Compile against an android.jar for the few framework types, e.g.:

    javac -cp android-34.jar -d out app/src/main/java/com/ledsync/phone/{AudioAnalyzer,DirectEffects,Tuning}.java tools/jvm-tests/AllEffectsTest.java
    java -cp out AllEffectsTest

`Matrix2DTest` renders every native 2D matrix effect on an 11x10 word clock into a PNG (argument: output file).

`YeelightModesTest` runs a fake Yeelight on 127.0.0.1:55443 (compile it with `YeelightLink.java`).

`LightChainTest` joins strip + lamp + strip into one strip and checks the effect travels across them (argument: PNG timeline).
