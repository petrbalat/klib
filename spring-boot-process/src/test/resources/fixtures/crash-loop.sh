#!/bin/sh
# padající přehrávač — po $1 sekundách (výchozí 0) skončí s kódem 3
sleep "${1:-0}"
exit 3
