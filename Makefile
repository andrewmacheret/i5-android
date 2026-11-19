# Android Studio's bundled JDK works, so no separate Java install is needed
JAVA_HOME ?= /Applications/Android Studio.app/Contents/jbr/Contents/Home
export JAVA_HOME
SDK ?= $(HOME)/Library/Android/sdk
ADB ?= $(SDK)/platform-tools/adb
EMULATOR ?= $(SDK)/emulator/emulator
AVD ?= Medium_Phone_API_37.0
DEBUG_APK = app/build/outputs/apk/debug/app-debug.apk
RELEASE_APK = app/build/outputs/apk/release/app-release.apk

.PHONY: test build install release run emulator screenshots scenario gallery lint clean

test:
	./gradlew :core:test

build:
	./gradlew :app:assembleDebug

install: build
	$(ADB) install -r $(DEBUG_APK)

release:
	./gradlew :app:assembleRelease
	$(ADB) install -r $(RELEASE_APK)

run: install
	$(ADB) shell am start -n com.andrewmacheret.i5/.ui.MainActivity

emulator:
	$(EMULATOR) -avd $(AVD) -no-snapshot-save > /dev/null 2>&1 &

screenshots: install
	scripts/screenshots.sh

# Debug builds only. NAME is one of the scenarios in Scenarios.kt, or live
scenario:
	$(ADB) shell am broadcast -n com.andrewmacheret.i5/.DebugReceiver --es scenario $(NAME)

gallery:
	$(ADB) shell am start -n com.andrewmacheret.i5/.WidgetGalleryActivity

lint:
	./gradlew :app:lintDebug

clean:
	./gradlew clean
