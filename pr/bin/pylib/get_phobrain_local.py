
# get_phobrain_local.sh is in pr/bin/, which should be in your path

PHOBRAIN_LOCAL = run_cmd('get_phobrain_local.sh').strip()

print('-- PHOBRAIN_LOCAL: ' + PHOBRAIN_LOCAL)

PHOBRAIN_LOCAL = os.path.expanduser(PHOBRAIN_LOCAL) + '/'

if not os.path.isdir(PHOBRAIN_LOCAL):
    print('-- Error: PHOBRAIN_LOCAL: Not a directory: ' + PHOBRAIN_LOCAL)
    exit(1)

