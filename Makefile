# Android Studio's bundled JDK works, so no separate Java install is needed.
# These are its default spots on macOS and Linux. If none exist, Gradle uses java from PATH.
STUDIO_JDKS = "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
	/opt/android-studio/jbr $(HOME)/android-studio/jbr /snap/android-studio/current/jbr \
	$(HOME)/.local/share/JetBrains/Toolbox/apps/android-studio/jbr
ifndef JAVA_HOME
  JAVA_HOME := $(shell for d in $(STUDIO_JDKS); do [ -d "$$d" ] && echo "$$d" && break; done)
endif
export JAVA_HOME
ifeq ($(shell uname),Darwin)
  DEFAULT_SDK = $(HOME)/Library/Android/sdk
else
  DEFAULT_SDK = $(HOME)/Android/Sdk
endif
ifndef SDK
  SDK := $(or $(ANDROID_HOME),$(ANDROID_SDK_ROOT),$(DEFAULT_SDK))
endif
# Gradle finds the SDK through this, so a fresh clone builds without local.properties
export ANDROID_HOME := $(SDK)
ADB ?= $(SDK)/platform-tools/adb
EMULATOR ?= $(SDK)/emulator/emulator
AVD ?= Medium_Phone_API_37.0
DEBUG_APK = app/build/outputs/apk/debug/app-debug.apk
RELEASE_APK = app/build/outputs/apk/release/app-release.apk

.PHONY: test build install release bundle run emulator screenshots scenario gallery lint clean

test:
	./gradlew :core:test

build:
	./gradlew :app:assembleDebug

install: build
	$(ADB) install -r $(DEBUG_APK)

# Signs with the release key from the Keychain if there is one, see scripts/signing.sh
release:
	scripts/signing.sh run ./gradlew :app:assembleRelease
	$(ADB) install -r $(RELEASE_APK)

# The app bundle Google Play wants, signed like release
bundle:
	scripts/signing.sh run ./gradlew :app:bundleRelease
	@echo "Wrote app/build/outputs/bundle/release/app-release.aab"

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
	$(ADB) shell am start -n com.andrewmacheret.i5/.WidgetGalleryActivity --ez more $(if $(MORE),true,false)

lint:
	./gradlew :app:lintDebug

clean:
	./gradlew clean
