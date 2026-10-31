# Atari Android Agent — Sega Genesis

تطبيق أندرويد يشغّل ألعاب **Sega Genesis / Mega Drive** عبر محرك Genesis Plus GX، مع عرض OpenGL ES، وتحكم لمسي متعدد النقاط، وإعدادات قابلة للتخصيص، وحفظ SRAM.

> **مهم:** لا تُضمَّن أي لعبة أو ROM داخل APK أو المستودع. استورد ملفًا تملك حق استخدامه من داخل التطبيق. المحرك مدمج تحت ترخيص Genesis Plus GX غير التجاري؛ راجع `THIRD_PARTY_NOTICES.txt` قبل أي توزيع.

## ما الذي يعمل الآن؟

- محاكاة Genesis فعلية عبر Genesis Plus GX، مع صوت وفيديو وSRAM.
- **Bilinear Filtering** لتنعيم الحواف، مفعّل افتراضيًا ويمكن إيقافه من الإعدادات.
- أزرار لمسية بشكل **D-pad + A/B/C + START**، متصلة فعليًا بمدخلات المحرك، مع دعم الضغط المتزامن (مثل يمين + B).
- تحريك مجموعات التحكم وتغيير الحجم والشفافية من الإعدادات، وحفظLayout تلقائيًا.
- استيراد ROM عبر Android Storage Access Framework، وإيقاف/استئناف، وإعادة ضبط اللعبة.
- حفظ تلقائي لـ SRAM لكل لعبة، واستعادته عند فتحها مجددًا.
- إشعارات التراخيص داخل التطبيق.

## بناء APK بأمر واحد

المتطلبات: **JDK 17**، **Android SDK Platform 35**، **Build Tools 35.0.0**، **NDK 27.0.12077973**، و**CMake 3.22.1**. اضبط `ANDROID_HOME` أو `local.properties`. لا تحتاج إلى Gradle عالميًا.

Linux / macOS:

```bash
./scripts/build-apk.sh debug
```

Windows:

```bat
scripts\build-apk.bat debug
```

أو مباشرة:

```bash
./gradlew :android:exportDebugApk
```

**الناتج القابل للتثبيت:**

```text
artifacts/apk/sega-android-debug.apk
```

التثبيت على جهاز متصل:

```bash
adb install -r artifacts/apk/sega-android-debug.apk
```

النسخة موقّعة بمفتاح debug. التفاصيل في **[دليل البناء](docs/BUILD_APK.md)**، ونتائج الاختبار الفعلية في **[سجل التحقق](docs/VERIFICATION.md)**.

## هيكل المشروع

```text
atari-android-agent/
├── android/                     # تطبيق Android: Activity، العرض، التحكم، الإعدادات
├── core/                        # JNI + Genesis Plus GX + منطق التحكم والاختبارات
│   └── src/main/cpp/genesis-plus-gx/
├── roms/                        # ملفات مصرّح بتوزيعها فقط؛ لا تُضمَّن افتراضيًا
├── scripts/                     # بناء وتصدير APK
├── docs/                        # البناء والتحقق
├── tests/                       # اختبار native smoke على المضيف
├── .github/workflows/           # بناء debug وإصدار release موقّع يدوي
├── THIRD_PARTY_NOTICES.txt      # تراخيص المحرك والمكونات
└── gradle/wrapper/              # Gradle 8.11.1 مثبت البصمة
```

## أوامر التحقق

```bash
./gradlew :core:test :android:testDebugUnitTest :android:lintDebug
./gradlew :android:exportDebugApk
```

اختبار اللمس الفعلي على محاكي (يتطلب ROM محليًا ولا يوزّعها):

```bash
adb install -r android/build/outputs/apk/debug/sega-android-debug.apk
adb install -r android/build/outputs/apk/androidTest/debug/sega-android-debug-androidTest.apk
adb push /path/to/game.gen /data/local/tmp/sega-test/game.gen
adb shell am instrument -w -r \
  -e class com.midoali.atari.ControlsInstrumentedTest \
  -e rom /data/local/tmp/sega-test/game.gen \
  com.midoali.atari.debug.test/androidx.test.runner.AndroidJUnitRunner
```

أضف `-e screenshots true` لالتقاط صور داخل التطبيق أثناء الاختبار.

## حالة التنفيذ

- [x] محرك Genesis حقيقي مربوط بالعرض والصوت والحفظ.
- [x] Bilinear filtering قابل للتبديل.
- [x] D-pad + A/B/C + START متصلون فعليًا، مع multitouch.
- [x] تحريك/تحجيم/شفافية التحكم وحفظ الإعدادات.
- [x] اختبار instrumentation ناجح على محاكي Android: لمس فعلي، استجابة RAM، multitouch، وتبديل الفلتر، وحفظ Layout بعد إعادة إنشاء الـ Activity.
- [x] بناء وتصدير APK وCI.
- [ ] اختبار على هاتف فعلي واسع التنوع (لم يُجرَ بعد).

## الأمان وحقوق الألعاب

- لا تضع كلمات المرور أو رموز الوصول أو مفاتيح التوقيع داخل Git.
- لا تُضمَّن ROMs إلا عند تمرير `--bundle-roms` أو `-PbundleRoms=true`، وبامتدادات `.a26/.bin/.a78/.md/.gen/.smd` فقط. استخدم فقط ملفات تملك حق استخدامها وتوزيعها.
- Genesis Plus GX ومكوناته لها تراخيص محددة؛ التوزيع التجاري غير مسموح به وفق ترخيص المحرك. راجع الإشعارات المرفقة.
- مفاتيح debug تختلف بين الأجهزة/تشغيلات CI؛ التحديث فوق توقيع مختلف قد يتطلب حذف النسخة القديمة. احفظ بياناتك أولًا.
