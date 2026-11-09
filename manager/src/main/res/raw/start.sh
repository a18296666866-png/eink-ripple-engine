#!/system/bin/sh
# Shizuku native starter bootstrap. %%%STARTER_PATH%%% is replaced by Starter.kt.
SOURCE_PATH="%%%STARTER_PATH%%%"
STARTER_PATH="/data/local/tmp/shizuku_starter"

echo "info: start.sh begin"
if [ ! -f "$SOURCE_PATH" ]; then
    echo "fatal: Shizuku starter source does not exist"
    exit 1
fi

# Never delete /data/local/tmp on copy failure: the directory may contain
# unrelated files belonging to the user or other tools.
if ! cp "$SOURCE_PATH" "$STARTER_PATH"; then
    echo "fatal: failed to copy native starter"
    exit 1
fi
chmod 700 "$STARTER_PATH" || exit 1
chown 2000 "$STARTER_PATH" 2>/dev/null || true
chgrp 2000 "$STARTER_PATH" 2>/dev/null || true

"$STARTER_PATH" "$@"
result=$?
if [ "$result" -eq 0 ]; then
    echo "info: shizuku_starter exit with 0"
else
    echo "info: shizuku_starter exit with non-zero value $result"
fi
exit "$result"
