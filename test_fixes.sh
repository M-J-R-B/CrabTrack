#!/bin/bash

echo "🧪 Quick test sequence for CrabTrack Fix Pack 01"
echo "================================================"

echo "1. Build the project..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo "✅ Build successful!"
    
    echo ""
    echo "2. Install on device/emulator..."
    ./gradlew installDebug
    
    echo ""
    echo "3. Manual testing checklist:"
    echo "   ✓ Launch app → Check Dashboard values update every 1-2s"
    echo "   ✓ Go to Settings → Set DO min = 7.0, Save"
    echo "   ✓ Return to Dashboard → Should show CRITICAL status"
    echo "   ✓ Check notification appears"
    echo "   ✓ Background app → Tap notification → Should open Alerts"
    echo "   ✓ Go to Molting → Wait 5-15s for stage changes"
    echo "   ✓ Check molting notifications for ECDYSIS/POSTMOLT_RISK"
    echo "   ✓ Settings validation → Enter invalid numbers → Save disabled"
    
    echo ""
    echo "4. Check logcat for any errors:"
    echo "   adb logcat -s TelemetryRepository AlertsNotifier MoltingNotifier"
    
else
    echo "❌ Build failed! Check the errors above."
fi