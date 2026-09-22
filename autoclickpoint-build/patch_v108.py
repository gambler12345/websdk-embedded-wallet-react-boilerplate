from pathlib import Path

src = Path(__file__).resolve().parent / "app/src/main/java/de/mpconsulting/autoclickpoint/AutoClickAccessibilityService.java"
text = src.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, got {count}")
    text = text.replace(old, new, 1)


replace_once(
    "import android.util.Base64;\n",
    "import android.util.Base64;\nimport android.util.DisplayMetrics;\n",
    "DisplayMetrics import",
)

replace_once(
    """    private static final long GUARD_MIN_CHECK_MS = 380;\n    private static final long SCREENSHOT_SETTLE_MS = 55;\n    private static final int GUARD_REGION_DP = 96;\n    private static final int DESCRIPTOR_SIDE = 16;\n    private static final double MATCH_THRESHOLD = 0.90;\n""",
    """    private static final long GUARD_MIN_CHECK_MS = 420;\n    private static final long SCREENSHOT_SETTLE_MS = 100;\n    // Focus more tightly on the actual tapped control instead of averaging too much surrounding UI.\n    private static final int GUARD_REGION_DP = 80;\n    // Higher spatial resolution prevents a changed icon/button from disappearing in down-sampling.\n    private static final int DESCRIPTOR_SIDE = 32;\n    // Fail-safe matcher: every condition below must pass.\n    private static final int PIXEL_CHANNEL_TOLERANCE = 24;\n    private static final double MAX_FULL_MEAN_CHANNEL_DIFF = 14.0;\n    private static final double MAX_CENTER_MEAN_CHANNEL_DIFF = 10.0;\n    private static final double MIN_FULL_CLOSE_PIXEL_RATIO = 0.90;\n    private static final double MIN_CENTER_CLOSE_PIXEL_RATIO = 0.93;\n    private static final double MAX_EDGE_MEAN_DIFF = 16.0;\n    // Two independent fresh screenshots must agree before each individual click.\n    private static final int GUARD_REQUIRED_CONSECUTIVE_MATCHES = 2;\n""",
    "strict guard constants",
)

replace_once(
    """    private boolean screenshotInFlight = false;\n    private long runGeneration = 0;\n""",
    """    private boolean screenshotInFlight = false;\n    private int guardMatchStreak = 0;\n    private long runGeneration = 0;\n""",
    "guard streak field",
)

replace_once(
    """    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }\n    private int sw() { return getResources().getDisplayMetrics().widthPixels; }\n    private int sh() { return getResources().getDisplayMetrics().heightPixels; }\n    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }\n""",
    """    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }\n    private int sw() { return realDisplaySize(true); }\n    private int sh() { return realDisplaySize(false); }\n\n    private int realDisplaySize(boolean width) {\n        try {\n            WindowManager manager = wm != null ? wm : (WindowManager) getSystemService(WINDOW_SERVICE);\n            if (manager != null && manager.getDefaultDisplay() != null) {\n                DisplayMetrics real = new DisplayMetrics();\n                manager.getDefaultDisplay().getRealMetrics(real);\n                int value = width ? real.widthPixels : real.heightPixels;\n                if (value > 0) return value;\n            }\n        } catch (Exception ignored) {}\n        DisplayMetrics fallback = getResources().getDisplayMetrics();\n        return width ? fallback.widthPixels : fallback.heightPixels;\n    }\n\n    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }\n""",
    "real display dimensions",
)

replace_once(
    """        imageGuardEnabled = !imageGuardEnabled;\n        prefs.edit().putBoolean(KEY_GUARD_ENABLED, imageGuardEnabled).apply();\n        guardWaiting = false;\n        updateGuardUi(null);\n""",
    """        imageGuardEnabled = !imageGuardEnabled;\n        prefs.edit().putBoolean(KEY_GUARD_ENABLED, imageGuardEnabled).apply();\n        guardWaiting = false;\n        guardMatchStreak = 0;\n        updateGuardUi(null);\n""",
    "toggle guard reset",
)

replace_once(
    """        guardReference = null;\n        imageGuardEnabled = false;\n        guardWaiting = false;\n""",
    """        guardReference = null;\n        imageGuardEnabled = false;\n        guardWaiting = false;\n        guardMatchStreak = 0;\n""",
    "clear reference reset",
)

replace_once(
    """        paused = false;\n        running = true;\n        guardWaiting = false;\n        long generation = ++runGeneration;\n""",
    """        paused = false;\n        running = true;\n        guardWaiting = imageGuardEnabled;\n        guardMatchStreak = 0;\n        long generation = ++runGeneration;\n""",
    "start strict waiting",
)

replace_once(
    """        running = false;\n        paused = true;\n        guardWaiting = false;\n        ++runGeneration;\n""",
    """        running = false;\n        paused = true;\n        guardWaiting = false;\n        guardMatchStreak = 0;\n        ++runGeneration;\n""",
    "pause streak reset",
)

replace_once(
    """        paused = false;\n        running = true;\n        guardWaiting = false;\n        long generation = ++runGeneration;\n        updateRunningUi();\n""",
    """        paused = false;\n        running = true;\n        guardWaiting = imageGuardEnabled;\n        guardMatchStreak = 0;\n        long generation = ++runGeneration;\n        updateRunningUi();\n""",
    "resume strict waiting",
)

replace_once(
    """        running = false;\n        paused = false;\n        guardWaiting = false;\n        ++runGeneration;\n""",
    """        running = false;\n        paused = false;\n        guardWaiting = false;\n        guardMatchStreak = 0;\n        ++runGeneration;\n""",
    "stop streak reset",
)

replace_once(
    """    private void setGuardWaiting(double similarity) {\n        guardWaiting = true;\n        if (startPause != null) {\n            startPause.setText(\"WARTET\");\n            startPause.setBackground(bg(Color.rgb(166, 103, 22), 18));\n        }\n        if (target != null) {\n            ((TextView) target).setText(\"?\");\n            target.setAlpha(.72f);\n        }\n        updateGuardUi(\"WARTET AUF BILD · \" + Math.round(similarity * 100) + \"%\");\n    }\n\n    private void setGuardMatched(double similarity) {\n        boolean wasWaiting = guardWaiting;\n        guardWaiting = false;\n        if (wasWaiting) updateRunningUi();\n        updateGuardUi(\"BILD OK · \" + Math.round(similarity * 100) + \"%\");\n    }\n""",
    """    private String guardScoreText(GuardScore score) {\n        return \"V\" + Math.round(score.fullSimilarity * 100)\n                + \" C\" + Math.round(score.centerSimilarity * 100)\n                + \" P\" + Math.round(score.centerCloseRatio * 100);\n    }\n\n    private void setGuardWaiting(GuardScore score) {\n        guardWaiting = true;\n        guardMatchStreak = 0;\n        if (startPause != null) {\n            startPause.setText(\"WARTET\");\n            startPause.setBackground(bg(Color.rgb(166, 103, 22), 18));\n        }\n        if (target != null) {\n            ((TextView) target).setText(\"?\");\n            target.setAlpha(.72f);\n        }\n        updateGuardUi(\"BILD FALSCH · WARTET · \" + guardScoreText(score));\n    }\n\n    private void setGuardVerifying(GuardScore score) {\n        guardWaiting = true;\n        if (startPause != null) {\n            startPause.setText(\"PRÜFT \" + guardMatchStreak + \"/\" + GUARD_REQUIRED_CONSECUTIVE_MATCHES);\n            startPause.setBackground(bg(Color.rgb(58, 92, 150), 18));\n        }\n        if (target != null) {\n            ((TextView) target).setText(\"…\");\n            target.setAlpha(.72f);\n        }\n        updateGuardUi(\"BILD BESTÄTIGEN \" + guardMatchStreak + \"/\"\n                + GUARD_REQUIRED_CONSECUTIVE_MATCHES + \" · \" + guardScoreText(score));\n    }\n\n    private void setGuardMatched(GuardScore score) {\n        guardWaiting = false;\n        updateRunningUi();\n        updateGuardUi(\"BILD OK · \" + guardScoreText(score));\n    }\n""",
    "guard UI methods",
)

replace_once(
    """                            guardReference = descriptor;\n                            imageGuardEnabled = true;\n                            guardWaiting = false;\n""",
    """                            guardReference = descriptor;\n                            imageGuardEnabled = true;\n                            guardWaiting = false;\n                            guardMatchStreak = 0;\n""",
    "learn streak reset",
)

replace_once(
    """                        double similarity = similarity(guardReference, descriptor);\n                        if (similarity >= MATCH_THRESHOLD) {\n                            setGuardMatched(similarity);\n                            dispatchSingleTap();\n                            long next = Math.max(GUARD_MIN_CHECK_MS, currentClickPeriodMs());\n                            scheduleGuardCheck(generation, next);\n                        } else {\n                            setGuardWaiting(similarity);\n                            scheduleGuardCheck(generation, GUARD_MIN_CHECK_MS);\n                        }\n""",
    """                        GuardScore score = compareDescriptors(guardReference, descriptor);\n                        if (score.matches) {\n                            guardMatchStreak++;\n                            if (guardMatchStreak < GUARD_REQUIRED_CONSECUTIVE_MATCHES) {\n                                // One matching frame is never enough to click. Confirm on a second fresh screenshot.\n                                setGuardVerifying(score);\n                                scheduleGuardCheck(generation, GUARD_MIN_CHECK_MS);\n                            } else {\n                                // Require two new confirmations again before the next click as well.\n                                guardMatchStreak = 0;\n                                setGuardMatched(score);\n                                dispatchSingleTap();\n                                long next = Math.max(GUARD_MIN_CHECK_MS, currentClickPeriodMs());\n                                scheduleGuardCheck(generation, next);\n                            }\n                        } else {\n                            // First mismatch immediately blocks every click and resets confirmation.\n                            setGuardWaiting(score);\n                            scheduleGuardCheck(generation, GUARD_MIN_CHECK_MS);\n                        }\n""",
    "strict verification loop",
)

replace_once(
    """        guardWaiting = purpose == SHOT_VERIFY;\n        updateGuardUi(text);\n""",
    """        guardWaiting = purpose == SHOT_VERIFY;\n        guardMatchStreak = 0;\n        updateGuardUi(text);\n""",
    "screenshot failure reset",
)

replace_once(
    """    private void hideOverlayForScreenshot() {\n        if (target != null) target.setAlpha(0f);\n        if (controls != null) controls.setAlpha(0f);\n    }\n\n    private void restoreOverlayAfterScreenshot() {\n        if (controls != null) controls.setAlpha(1f);\n        if (target != null) {\n            if (running) target.setAlpha(guardWaiting ? .72f : .62f);\n            else if (paused) target.setAlpha(.82f);\n            else target.setAlpha(1f);\n        }\n    }\n""",
    """    private void hideOverlayForScreenshot() {\n        // INVISIBLE is stricter than alpha=0: the overlay cannot contaminate the reference crop.\n        if (target != null) target.setVisibility(View.INVISIBLE);\n        if (controls != null) controls.setVisibility(View.INVISIBLE);\n    }\n\n    private void restoreOverlayAfterScreenshot() {\n        if (controls != null) controls.setVisibility(View.VISIBLE);\n        if (target != null) {\n            target.setVisibility(View.VISIBLE);\n            if (running) target.setAlpha(guardWaiting ? .72f : .62f);\n            else if (paused) target.setAlpha(.82f);\n            else target.setAlpha(1f);\n        }\n    }\n""",
    "strict overlay exclusion",
)

replace_once(
    """    private double similarity(byte[] reference, byte[] current) {\n        if (reference == null || current == null || reference.length != current.length || reference.length == 0) return 0.0;\n        long diff = 0;\n        for (int i = 0; i < reference.length; i++) {\n            diff += Math.abs((reference[i] & 0xff) - (current[i] & 0xff));\n        }\n        double maxDiff = 255.0 * reference.length;\n        return Math.max(0.0, Math.min(1.0, 1.0 - diff / maxDiff));\n    }\n""",
    """    private static final class GuardScore {\n        final boolean matches;\n        final double fullSimilarity;\n        final double centerSimilarity;\n        final double fullCloseRatio;\n        final double centerCloseRatio;\n        final double edgeMeanDiff;\n\n        GuardScore(boolean matches, double fullSimilarity, double centerSimilarity,\n                   double fullCloseRatio, double centerCloseRatio, double edgeMeanDiff) {\n            this.matches = matches;\n            this.fullSimilarity = fullSimilarity;\n            this.centerSimilarity = centerSimilarity;\n            this.fullCloseRatio = fullCloseRatio;\n            this.centerCloseRatio = centerCloseRatio;\n            this.edgeMeanDiff = edgeMeanDiff;\n        }\n    }\n\n    private int channel(byte[] data, int pixel, int offset) {\n        return data[pixel * 3 + offset] & 0xff;\n    }\n\n    private int luminance(byte[] data, int pixel) {\n        int r = channel(data, pixel, 0);\n        int g = channel(data, pixel, 1);\n        int b = channel(data, pixel, 2);\n        return (77 * r + 150 * g + 29 * b) >> 8;\n    }\n\n    private GuardScore compareDescriptors(byte[] reference, byte[] current) {\n        int expected = DESCRIPTOR_SIDE * DESCRIPTOR_SIDE * 3;\n        if (reference == null || current == null || reference.length != expected || current.length != expected) {\n            return new GuardScore(false, 0, 0, 0, 0, 255);\n        }\n\n        long fullChannelDiff = 0;\n        long centerChannelDiff = 0;\n        int fullClosePixels = 0;\n        int centerClosePixels = 0;\n        int fullPixels = DESCRIPTOR_SIDE * DESCRIPTOR_SIDE;\n        int centerPixels = 0;\n\n        int centerStart = DESCRIPTOR_SIDE / 4;\n        int centerEnd = DESCRIPTOR_SIDE - centerStart;\n\n        for (int y = 0; y < DESCRIPTOR_SIDE; y++) {\n            for (int x = 0; x < DESCRIPTOR_SIDE; x++) {\n                int pixel = y * DESCRIPTOR_SIDE + x;\n                int dr = Math.abs(channel(reference, pixel, 0) - channel(current, pixel, 0));\n                int dg = Math.abs(channel(reference, pixel, 1) - channel(current, pixel, 1));\n                int db = Math.abs(channel(reference, pixel, 2) - channel(current, pixel, 2));\n                int channelDiff = dr + dg + db;\n                int maxChannelDiff = Math.max(dr, Math.max(dg, db));\n                fullChannelDiff += channelDiff;\n                if (maxChannelDiff <= PIXEL_CHANNEL_TOLERANCE) fullClosePixels++;\n\n                if (x >= centerStart && x < centerEnd && y >= centerStart && y < centerEnd) {\n                    centerPixels++;\n                    centerChannelDiff += channelDiff;\n                    if (maxChannelDiff <= PIXEL_CHANNEL_TOLERANCE) centerClosePixels++;\n                }\n            }\n        }\n\n        double fullMeanDiff = fullChannelDiff / Math.max(1.0, fullPixels * 3.0);\n        double centerMeanDiff = centerChannelDiff / Math.max(1.0, centerPixels * 3.0);\n        double fullCloseRatio = fullClosePixels / Math.max(1.0, fullPixels);\n        double centerCloseRatio = centerClosePixels / Math.max(1.0, centerPixels);\n\n        long edgeDiffSum = 0;\n        int edgeComparisons = 0;\n        for (int y = 0; y < DESCRIPTOR_SIDE; y++) {\n            for (int x = 0; x < DESCRIPTOR_SIDE; x++) {\n                int p = y * DESCRIPTOR_SIDE + x;\n                if (x + 1 < DESCRIPTOR_SIDE) {\n                    int right = p + 1;\n                    int refEdge = Math.abs(luminance(reference, p) - luminance(reference, right));\n                    int curEdge = Math.abs(luminance(current, p) - luminance(current, right));\n                    edgeDiffSum += Math.abs(refEdge - curEdge);\n                    edgeComparisons++;\n                }\n                if (y + 1 < DESCRIPTOR_SIDE) {\n                    int down = p + DESCRIPTOR_SIDE;\n                    int refEdge = Math.abs(luminance(reference, p) - luminance(reference, down));\n                    int curEdge = Math.abs(luminance(current, p) - luminance(current, down));\n                    edgeDiffSum += Math.abs(refEdge - curEdge);\n                    edgeComparisons++;\n                }\n            }\n        }\n        double edgeMeanDiff = edgeDiffSum / Math.max(1.0, edgeComparisons);\n\n        double fullSimilarity = Math.max(0.0, 1.0 - fullMeanDiff / 255.0);\n        double centerSimilarity = Math.max(0.0, 1.0 - centerMeanDiff / 255.0);\n\n        boolean matches = fullMeanDiff <= MAX_FULL_MEAN_CHANNEL_DIFF\n                && centerMeanDiff <= MAX_CENTER_MEAN_CHANNEL_DIFF\n                && fullCloseRatio >= MIN_FULL_CLOSE_PIXEL_RATIO\n                && centerCloseRatio >= MIN_CENTER_CLOSE_PIXEL_RATIO\n                && edgeMeanDiff <= MAX_EDGE_MEAN_DIFF;\n\n        return new GuardScore(matches, fullSimilarity, centerSimilarity,\n                fullCloseRatio, centerCloseRatio, edgeMeanDiff);\n    }\n""",
    "multi-metric matcher",
)

src.write_text(text, encoding="utf-8")
print("v1.0.8 strict image-guard patch applied")
