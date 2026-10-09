#!/bin/sh
# zaseknutý přehrávač — SIGTERM ignoruje (sleep ve smyčce, aby trap platil i pro shell)
trap '' TERM
while true; do sleep 1; done
