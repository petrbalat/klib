#!/bin/sh
# napodobuje Chromium — rodič se dvěma dlouho běžícími potomky (renderery)
sleep 1000 &
sleep 1000 &
wait
