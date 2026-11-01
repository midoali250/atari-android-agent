# بناء وتصدير APK

## ما الذي يبنيه المشروع الآن؟

المشروع يحتوي على تطبيق أندرويد يشغّل ألعاب Sega Genesis / Mega Drive عبر Genesis Plus GX، مع يد سيجا 6 أزرار، أوضاع صورة Original/Smooth/Enhanced عبر Canvas/Bitmap، عرض كامل بدون قص، زر ترس لقائمة جانبية، وحفظ SRAM. **لا تُضمَّن أي ROM افتراضيًا.** استورد اللعبة من داخل التطبيق بعد التثبيت، أو ابنِ نسخة شخصية مضمّنة كما في القسم 4.1.

الإعدادات الحالية:

- Gradle Wrapper: **8.11.1**، مع تثبيت SHA-256 لتوزيعة Gradle.
- Android Gradle Plugin: **8.9.2**.
- JDK المطلوب: **17**.
- Android SDK Platform: **35**؛ Build Tools: **35.0.0**.
- أقل إصدار أندرويد: **Android 8.0 / API 26**.
- `compileSdk` و`targetSdk`: **35**.
- APK واحد لمعماريات `arm64-v8a` و`armeabi-v7a` و`x86_64` في release، ويضيف debug معمارية `x86` للمحاكي. تُبنى مكتبة JNI عبر NDK 27.0.12077973 وCMake 3.22.1.
- معرّف نسخة التطوير: `com.midoali.atari.debug`؛ معرّف الإصدار: `com.midoali.atari`.

## 1. تجهيز البيئة محليًا

ثبّت JDK 17 وAndroid Studio، ثم ثبّت Android SDK Platform 35 وBuild Tools 35.0.0 وNDK 27.0.12077973 وCMake 3.22.1 من SDK Manager. افتح **مجلد جذر المستودع** في Android Studio، وليس `android/` وحده.

اضبط `ANDROID_HOME` على مسار SDK، أو أنشئ `local.properties` محليًا بالقيمة المناسبة لجهازك، مثل:

```properties
# Linux/macOS example; choose the actual SDK location on your machine.
sdk.dir=/absolute/path/to/Android/Sdk
```

```properties
# Windows example; use your actual SDK location.
sdk.dir=C\:\\Users\\YOUR_NAME\\AppData\\Local\\Android\\Sdk
```

`local.properties` مستثنى من Git. لا تضع كلمات مرور أو مفاتيح توقيع فيه. لا تحتاج إلى تثبيت Gradle عالميًا؛ استخدم Wrapper المرفق.

## 2. أمر واحد لنسخة تطوير قابلة للتثبيت

من جذر المستودع على Linux أو macOS:

```bash
./scripts/build-apk.sh debug
```

على Windows:

```bat
scripts\build-apk.bat debug
```

أو باستخدام Gradle مباشرة على أي نظام:

```bash
./gradlew :android:exportDebugApk
# Windows: gradlew.bat :android:exportDebugApk
```

**الملف الناتج:**

```text
artifacts/apk/sega-android-debug.apk
```

يبني الأمر التطبيق ويوقّعه بمفتاح debug المحلي ثم ينسخه إلى المسار الثابت أعلاه. توقيع debug مناسب للتجربة والتثبيت اليدوي، وليس للتوزيع بوصفه إصدارًا نهائيًا. يمكن أن تختلف هوية المفتاح بين الأجهزة أو بين تشغيلات CI؛ تحديث تطبيق موقّع بمفتاح مختلف يتطلب حذف النسخة القديمة أولًا. لا تحذف بيانات مهمة قبل عمل نسخة احتياطية.

### تثبيت APK على جهاز متصل

فعّل USB debugging، ووصّل الجهاز، واقبل تفويض التصحيح. بعد نجاح البناء:

```bash
adb devices
adb install -r artifacts/apk/sega-android-debug.apk
adb shell am start -n com.midoali.atari.debug/com.midoali.atari.MainActivity
```

ولإزالة نسخة التطوير:

```bash
adb uninstall com.midoali.atari.debug
```

يمكن بدلًا من ذلك نقل APK إلى الهاتف وفتحه، مع السماح بالتثبيت من المصدر الذي تنقله منه. لا تعطِ صلاحية تثبيت من مصادر غير معروفة بشكل دائم.

## 3. الاختبارات وفحص البناء

```bash
./gradlew :core:test :android:testDebugUnitTest :android:lintDebug
```

ولبناء نظيف من الصفر:

```bash
./gradlew clean :android:exportDebugApk
```

للتحقق من التوقيع والمحاذاة بعد البناء:

```bash
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs artifacts/apk/sega-android-debug.apk
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -c -p 4 artifacts/apk/sega-android-debug.apk
```

على Windows استبدل `$ANDROID_HOME` بمسار SDK المناسب، واستخدم أدوات Build Tools الموجودة داخله.

## 4. تضمين ملفات ROM اختياريًا

**لا تُضمَّن أي ملفات ROM افتراضيًا.** ضع فقط ملفات تملك حق استخدامها وتوزيعها داخل `roms/`. الامتدادات التي تُجمع عند تفعيل الخيار: `.a26` و`.bin` و`.a78` و`.md` و`.gen` و`.smd`، دون المجلدات الفرعية. هذه أسماء امتدادات للتغليف فقط؛ التطبيق يستورد ألعاب Genesis عبر واجهة الاستيراد بغض النظر عن هذه القائمة.

```bash
./scripts/build-apk.sh debug --bundle-roms
# Equivalent:
./gradlew :android:exportDebugApk -PbundleRoms=true
```

Windows:

```bat
scripts\build-apk.bat debug --bundle-roms
```

تُنسخ الملفات إلى أصول APK تحت `assets/roms/`. لا يشغّل التطبيق هذه الأصول تلقائيًا؛ الاستيراد الفعلي للعب يتم عبر Storage Access Framework إلى مساحة التطبيق الخاصة. يفشل البناء إذا طلبت التضمين ولم توجد ملفات مناسبة. بناء آخر مع `debug` بدون الخيار، أو `-PbundleRoms=false`، يمسح ملفات ROM من مجلد الأصول المولّد حتى لا تتسرب إلى البناء التالي.

لا يعني استثناء الملفات من البناء أنها مستثناة تلقائيًا من Git. راجع الترخيص قبل إضافة أي ROM إلى المستودع.

### 4.1 نسخة شخصية مضمّنة اللعبة (تعمل مباشرة)

لتضمين لعبة تملك حق استخدامها وتشغيلها تلقائيًا دون شاشة استيراد:

```bash
cp /path/to/game.md roms/game.md
./gradlew :android:exportReleaseApk \
  -PbundleRoms=true -PpersonalGame=true \
  -PgameApplicationId=com.midoali.umk3.v5 -PgameLabel=UMK3
```

- `personalGame=true` يخفي زر الاستيراد ويشغّل أول ROM مضمّنة تلقائيًا.
- استخدم `gameApplicationId` مستقلًا إذا كان مفتاح التوقيع مختلفًا عن نسخة سابقة، حتى تُثبَّت كتطبيق مستقل بدون تعارض مع النسخة القديمة.
- ملف ROM يبقى خارج Git عبر `.gitignore`؛ لا تدفعه إلى المستودع.
- الناتج في `artifacts/apk/sega-android-release.apk` بعد ضبط متغيرات التوقيع.

## 5. إصدار release موقّع بمفتاحك

لا يُنشئ المشروع نسخة release غير موقّعة على أنها ملف قابل للتثبيت. أمر release يتوقف برسالة واضحة إذا لم تُضبط بيانات التوقيع.

### إنشاء مفتاح محلي للاختبار

احتفظ بالمفتاح خارج المستودع، واحفظ نسخة احتياطية آمنة منه. فقدان المفتاح يمنع إصدار تحديثات بنفس التوقيع.

```bash
keytool -genkeypair -v \
  -keystore "$HOME/.android/atari-release.jks" \
  -alias atari -keyalg RSA -keysize 2048 -validity 10000
```

### ضبط متغيرات البيئة ثم البناء

اضبط القيم في الجلسة الآمنة أو أداة إدارة الأسرار، ولا تكتبها داخل ملفات المشروع:

```bash
export ATARI_KEYSTORE_PATH="$HOME/.android/atari-release.jks"
export ATARI_KEYSTORE_PASSWORD="set-in-your-secret-manager"
export ATARI_KEY_ALIAS="atari"
export ATARI_KEY_PASSWORD="set-in-your-secret-manager"
./scripts/build-apk.sh release
```

على Windows استخدم `$env:ATARI_KEYSTORE_PATH` وبقية المتغيرات بالقيم الحقيقية، ثم:

```bat
scripts\build-apk.bat release
```

الناتج:

```text
artifacts/apk/sega-android-release.apk
```

يستخدم release تصغير الشيفرة والموارد عبر R8. راجع `android/proguard-rules.pro` واختبر الإصدار على جهاز عند إضافة المحرك أو مكتبات تستخدم reflection/JNI.

## 6. تنزيل APK من GitHub Actions

### نسخة تطوير تلقائية

مسار العمل `.github/workflows/android-apk.yml` يعمل عند الدفع إلى `main`، وعند pull request، أو يدويًا عبر **Actions → Android APK → Run workflow**.

بعد نجاح التشغيل، نزّل الأرشيف من **Artifacts → sega-android-debug-apk**، ثم فك الضغط للحصول على APK وملف SHA-256. يشغّل المسار الاختبارات وlint، ويتحقق من توقيع APK والمحاذاة قبل رفعه. لا يحتاج إلى مفتاح release أو أسرار المستودع.

### إصدار موقّع اختياري

مسار العمل `.github/workflows/android-release.yml` يدوي فقط، ويعمل على `main`. اضبط هذه الأسرار في **Settings → Secrets and variables → Actions** أولًا:

| Secret | القيمة |
| --- | --- |
| `ATARI_KEYSTORE_BASE64` | ترميز Base64 لملف keystore الخاص بك |
| `ATARI_KEYSTORE_PASSWORD` | كلمة مخزن المفاتيح |
| `ATARI_KEY_ALIAS` | اسم alias للمفتاح |
| `ATARI_KEY_PASSWORD` | كلمة المفتاح |

ثم شغّل **Signed Android release**. خيار تضمين ROM مغلق افتراضيًا؛ فعّله فقط إذا كانت حقوق التوزيع تسمح. الناتج في **sega-android-release-apk**. يُفك المفتاح إلى ملف مؤقت أثناء البناء ويُمسح بعده. هذه الأسرار غير مضبوطة تلقائيًا، ولم يُضف أي مفتاح توقيع إلى Git.

ملفات APK وArtifacts ليست بديلًا عن نشر Google Play. لا يوجد نشر تلقائي إلى المتجر في هذه الإعدادات.

## 7. تحديث الإصدارات واستكشاف الأخطاء

- قبل كل إصدار جديد، زد `versionCode` رقمًا صحيحًا موجبًا و`versionName` وصفيًا في `android/build.gradle.kts`.
- **SDK location not found:** اضبط `ANDROID_HOME` أو `sdk.dir` في `local.properties`.
- **Unsupported Java / invalid target release:** استخدم JDK 17، وتحقق من `JAVA_HOME` ومن Gradle JDK داخل Android Studio.
- **Release signing not configured:** اضبط متغيرات signing الأربعة، أو ابنِ debug للتجربة.
- **INSTALL_FAILED_UPDATE_INCOMPATIBLE:** مفتاح توقيع النسخة الموجودة مختلف. استخدم المفتاح الأصلي أو احذف النسخة القديمة بعد حفظ بياناتها.
- **تنزيل Gradle أو تبعياته فشل:** يتطلب أول بناء اتصالًا بالإنترنت للوصول إلى مستودعات Gradle وGoogle وMaven Central.

اختبار اللمس الفعلي على المحاكي موثق في [سجل التحقق](VERIFICATION.md). لم يُجرَ بعد اختبار واسع على هواتف فعلية متنوعة.
