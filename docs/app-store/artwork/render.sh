#!/bin/sh
# Renders artwork.html to the App Store header and search-result images.
# Usage: docs/app-store/artwork/render.sh
set -e
cd "$(dirname "$0")"
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
render() { # <variant> <width> <height> <output>
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
    --allow-file-access-from-files --virtual-time-budget=3000 \
    --window-size="$2,$3" --screenshot="$PWD/$4.png" "file://$PWD/artwork.html#$1" 2>/dev/null
  sips -s format jpeg -s formatOptions 92 "$4.png" --out "$4.jpg" >/dev/null && rm "$4.png"
  echo "$4.jpg: $(sips -g pixelWidth -g pixelHeight "$4.jpg" | awk '/pixel/{printf $2" "}')"
}
render header 3840 1646 header-3840x1646
render search 3840 2560 search-3840x2560
