#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

set -e

# 40K pics -> 6G .pairs, want 2 at once

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

time java -Xmx60g -jar $SCRIPT_DIR/build/libs/predtool-all-1.0.jar $*
