#!/bin/sh
# napodobuje chrome_crashpad_handler: potomek se odpojí do nové session (setsid) a rodič hned skončí
setsid sh -c 'sleep 1000' > /dev/null 2>&1 < /dev/null &
exit 0
