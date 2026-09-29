#!/usr/bin/env bash
set -euo pipefail

mkdir -p app/src/main/res/drawable-nodpi
mkdir -p app/src/main/res/mipmap-xxxhdpi

base64 --decode branding/brother_matrizes_brand_logo.base64 \
  > app/src/main/res/drawable-nodpi/brother_matrizes_brand_logo.jpg

cp app/src/main/res/drawable-nodpi/brother_matrizes_brand_logo.jpg \
  app/src/main/res/mipmap-xxxhdpi/ic_launcher_brother_matrizes.jpg

test -s app/src/main/res/drawable-nodpi/brother_matrizes_brand_logo.jpg
test -s app/src/main/res/mipmap-xxxhdpi/ic_launcher_brother_matrizes.jpg
