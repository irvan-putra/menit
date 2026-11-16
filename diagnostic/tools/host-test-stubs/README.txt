Host-only check of the real DiagStore against minimal Android stubs (android/**). Not part of the Gradle build or the APK.
Run: sh tools/host-test-stubs/run.sh   (JDK 11+). CI runs it in build.yml and sign-diag-apk.yml.
Scenarios 1-7: archive in foreground, kill after archive, archive in background, rotation then kill, rotation then clean close,
rotation landing on the FS write, rotation whose rename fails.
