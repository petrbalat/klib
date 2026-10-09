#!/bin/sh
# spadlý wrapper (oosplash → soffice.bin, sh -c "mpv …") — program běží dál, PID zapíše do $1, wrapper skončí s kódem 1
sleep 1000 > /dev/null 2>&1 &
echo $! >> "$1"
exit 1
