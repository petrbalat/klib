#!/bin/sh
# upovídaný přehrávač — výstup větší než buffer roury (64 KB)
head -c "${1:-200000}" /dev/zero | tr '\0' 'x'
