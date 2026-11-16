#!/bin/sh
# Local build without Gradle: javac + aapt2 + d8 + apksigner. Usage: tools/build-local.sh SDK_DIR VERSION
set -e
SDK=$1; VER=$2
BT=$SDK/build-tools/34.0.0; JAR=$SDK/platforms/android-34/android.jar
OUT=build/local; rm -rf $OUT; mkdir -p $OUT/classes $OUT/res
$BT/aapt2 compile --dir app/src/main/res -o $OUT/res.zip
sed 's|<manifest |<manifest package="com.vanipy.menit" |' app/src/main/AndroidManifest.xml > $OUT/AndroidManifest.xml
$BT/aapt2 link -o $OUT/base.apk -I $JAR --manifest $OUT/AndroidManifest.xml \
  --min-sdk-version 26 --target-sdk-version 34 --version-code 1 --version-name "$VER" \
  -R $OUT/res.zip --auto-add-overlay
javac -source 8 -target 8 -Xlint:-options -cp $JAR -d $OUT/classes app/src/main/java/com/vanipy/menit/*.java
$BT/d8 --min-api 26 --lib $JAR --output $OUT $(find $OUT/classes -name '*.class')
cp $OUT/base.apk $OUT/unsigned.apk
(cd $OUT && zip -q -j unsigned.apk classes.dex)
$BT/zipalign -f 4 $OUT/unsigned.apk $OUT/aligned.apk
[ -f $OUT/debug.keystore ] || keytool -genkeypair -keystore $OUT/debug.keystore -storepass android -keypass android \
  -alias k -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Menit local test" 2>/dev/null
$BT/apksigner sign --ks $OUT/debug.keystore --ks-pass pass:android --out $OUT/menit-v$VER-local.apk $OUT/aligned.apk
$BT/apksigner verify $OUT/menit-v$VER-local.apk && echo OK
