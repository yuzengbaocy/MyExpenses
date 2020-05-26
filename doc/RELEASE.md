# Steps for preparing a new release
  
* Set version info in build.gradle
* Check that master is merged into distribution branch
* check if version_codes, version_names, upgrade.xml use the correction version code
* if applicable publish announcement on Google+ and Facebook and add links
* Test and assemble (replace play with amazon or blackberry if needed)
  * ./gradlew lintPlayWithDriveWithAdsGmsRelease
  * ./gradlew testPlayWithDriveWithAdsGmsReleaseUnitTest
  * ./gradlew clean connectedPlayWithDriveWithAdsGmsForTestAndroidTest
  * (eventually) adb shell settings put secure show_ime_with_hard_keyboard 0
  * ./gradlew clean bundlePlayWithDriveWithAdsGmsRelease
* test upgrade mechanism
* Create release tag in GIT (git tag r39gp; git push gitlab r39gp)
* mv APK and mapping.txt into a new folder in releases
* upload to Play
* add recent changes in Market
* update _config.yml and push gh-pages
