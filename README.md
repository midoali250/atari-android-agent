# Atari Android Agent

مشروع أندرويد لألعاب Atari، مع منظومة بناء وتصدير APK وتكامل مستمر عبر GitHub Actions.

> **الحالة الحالية:** يوجد تطبيق مبدئي قابل للبناء والتثبيت للتحقق من التغليف، لكن **لا يوجد محرك محاكاة أو لعبة قابلة للعب بعد**. لا تُضمَّن ملفات ROM افتراضيًا.

## بناء APK بأمر واحد

المتطلبات: **JDK 17**، **Android SDK Platform 35** و**Build Tools 35.0.0**. اضبط `ANDROID_HOME` أو `local.properties` المحلي. لا تحتاج إلى تثبيت Gradle عالميًا.

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
# Windows: gradlew.bat :android:exportDebugApk
```

**الناتج القابل للتثبيت يدويًا:**

```text
artifacts/apk/atari-android-debug.apk
```

النسخة موقّعة بمفتاح debug. للتثبيت على جهاز متصل بعد تفعيل USB debugging:

```bash
adb install -r artifacts/apk/atari-android-debug.apk
```

دليل البيئة، التوقيع، إصدار release، تضمين ROM الاختياري، وتنزيل APK من GitHub Actions موجود في **[دليل بناء وتصدير APK](docs/BUILD_APK.md)**. نتائج الفحص المحلي وحدوده موثقة في **[سجل التحقق](docs/VERIFICATION.md)**.

## هيكل المشروع

```text
atari-android-agent/
├── android/                     # تطبيق Android وGradle module
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/                # Activity وموارد الواجهة
│       └── test/                # اختبارات هوية نسخة البناء
├── core/                        # وحدة Java مستقلة؛ ليست محرك محاكاة بعد
│   ├── build.gradle.kts
│   └── src/
├── roms/                        # ملفات مصرّح بتوزيعها؛ لا تُضمَّن افتراضيًا
├── scripts/
│   ├── build-apk.sh
│   └── build-apk.bat
├── docs/BUILD_APK.md
├── .github/workflows/
│   ├── android-apk.yml          # بناء واختبار وتصدير debug APK
│   └── android-release.yml      # إصدار موقّع يدوي عند ضبط الأسرار
├── artifacts/apk/               # ناتج محلي مستثنى من Git
├── gradle/wrapper/              # Gradle 8.11.1 مثبت البصمة
├── build.gradle.kts             # Android Gradle Plugin 8.9.2
├── settings.gradle.kts
├── gradle.properties
├── gradlew
└── gradlew.bat
```

افتح **جذر المستودع** في Android Studio ليظهر مشروعا `:android` و`:core` معًا.

## أوامر التحقق

```bash
./gradlew :core:test :android:testDebugUnitTest :android:lintDebug
./gradlew clean :android:exportDebugApk
```

يصدر المسار `artifacts/apk/` ملفًا باسم ثابت، ويُستثنى من Git. يرفع GitHub Actions ملف APK وبصمة SHA-256 في Artifacts بعد نجاح الاختبارات وفحص التوقيع والمحاذاة.

## حالة التنفيذ

- [x] إنشاء المجلدات الأساسية والتوثيق.
- [x] إعداد Gradle Wrapper وتطبيق أندرويد مبدئي ووحدة Java مستقلة.
- [x] بناء APK تطوير موقّع وتصديره بأمر واحد.
- [x] إعداد توقيع release اختياري دون وضع مفاتيح في المستودع.
- [x] تضمين ROM اختياري، مغلق افتراضيًا.
- [x] إعداد مسارات بناء وتصدير عبر GitHub Actions.
- [ ] اختيار أول لعبة ونظام Atari المستهدف.
- [ ] تنفيذ محرك المحاكاة وربط العرض والصوت والتحكم.
- [ ] اختبار اللعب الفعلي على أجهزة أندرويد وتحسين الأداء.

## الأمان وحقوق الألعاب

- لا تضع كلمات المرور أو رموز الوصول أو مفاتيح التوقيع داخل Git. مفاتيح release ومتغيرات توقيعها موضحة في دليل البناء.
- لا تُضمَّن ROMs إلا عند تمرير `--bundle-roms` أو `-PbundleRoms=true`. استخدم فقط ملفات تملك حق استخدامها وتوزيعها.
- نسخ CI التطويرية قد تستخدم مفاتيح debug مختلفة بين الأجهزة أو التشغيلات. التحديث فوق نسخة موقّعة بمفتاح مختلف قد يتطلب حذف النسخة القديمة؛ احفظ بياناتك أولًا.

## الخطوة التالية

اختيار اللعبة الأولى ونظام Atari، ثم تنفيذ المحاكاة الحقيقية. **نجاح بناء APK الحالي يتحقق من التغليف فقط، ولا يعني أن لعبة أصبحت جاهزة للعب.**
