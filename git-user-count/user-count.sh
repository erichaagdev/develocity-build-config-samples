#!/bin/bash
set -e

basedir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
repositories=$basedir/repositories.txt
userListFile="$basedir/gradle-enterprise-users.txt"
userUniqueListFile="$basedir/gradle-enterprise-unique-users.txt"
usersByRepoFile="$basedir/gradle-enterprise-users-by-repo.csv"
checkout_area=.tmp/repos
since=30
shallow_clone=true
git_options=

yellow='\033[1;33m'
nc='\033[0m'

# -b option lets you specify a branch to checkout, store it in a variable
# -s option lets you specify a number of days to count commits from, store it in a variable otherwise default to 30
# -c option disables shallow cloning
# -o option lets you specify aditional git cloning options
while getopts b:s:co: option
do
  case "${option}" in
    b) branch=${OPTARG};;
    s) since=${OPTARG};;
    c) shallow_clone=false;;
    o) git_options=${OPTARG};;
    *) echo "Usage: $0 [-b branch_name] [-s since_days] [-o git_options] [-c]" >&2
       exit 1;;
  esac
done

# Check that both -s and -c cannot be specified at the same time
if [ "$shallow_clone" = false ] && [ -n "$since" ]; then
  echo "Cannot specify both -s and -c options at the same time" >&2
  exit 1
fi

function prepare() {
  echo -n "" > "$userListFile"
  echo -n "" > "$userUniqueListFile"
  echo "repository,users" > "$usersByRepoFile"
}

function print_information() {
  echo "Counting commits from the last $since days"

  if [ -z "$branch" ]; then
    echo "No branch name specified, counting commits on default branch"
  else
    echo "Branch name is $branch"
  fi
}

function process_repositories() {
  numOfRepos=$(sed "/^[[:blank:]]*#/d;s/^[[:blank:]]*//;s/#.*//" "$repositories" | wc -l | tr -d '[:space:]')
  current=1
  while IFS= read -r repo
  do
    echo -e -n "${yellow}($current/$numOfRepos) Processing ${repo}...${nc}"
    process_repository "$repo"
    ((current++))
  done < <(sed "/^[[:blank:]]*#/d;s/^[[:blank:]]*//;s/#.*//" "$repositories")

  # remove duplicates in list of users
  sort -u "$userListFile" > "$userUniqueListFile"
}

function process_repository() {
  repository_name="${1//\//-}"
  
  # Check if repository is valid
  if [ -z "$repository_name" ]; then
    echo "Repository name is empty" >&2
    exit 1
  fi
  # If repository is already cloned, switch to specified branch
  if [ -d "$checkout_area/$repository_name" ]; then
    echo "already cloned."
    if [ -n "$branch" ]; then
      pushd "$checkout_area/$repository_name" >& /dev/null || return
      git checkout "$branch"
      popd >& /dev/null || return
    fi
  else
    git_clone_command="git clone --no-checkout  --filter=tree:0 --single-branch --no-tags $1 $checkout_area/$repository_name $git_options --quiet"

    # If branch is specified, append it to the git clone command
    if [ -n "$branch" ]; then
      git_clone_command="${git_clone_command} --branch $branch"
    fi
    
    # If shallow clone is enabled, append it to the git clone command
    if [ "$shallow_clone" = true ]; then
      git_clone_command="${git_clone_command} --shallow-since=\"${since} days ago\""
    fi

    eval "${git_clone_command}" && echo -e "${yellow}done${nc}"
  fi

  pushd "$checkout_area/$repository_name" >& /dev/null || return
  git reset HEAD . >& /dev/null

  # append unique git usernames from commits in the last X days to a file
  git log --format="%ae" --since="${since}".day | sort -u >> "$userListFile"

  # append the number of unique git committers in the last X days to a file
  git log --format="%ae" --since="${since}".day | sort -u | wc -l | xargs echo "$1," >> "$usersByRepoFile"

  popd >& /dev/null || return
}

function print_results() {
  echo ""
  echo "All cloned repositories available at $checkout_area"
  echo "Unique usernames are stored in $(basename "$userUniqueListFile")"
  echo "User counts by repository are stored in $(basename "$usersByRepoFile")"

  echo "Total number of unique users: $(wc -l < "$userUniqueListFile")"
  echo "Note: this user count may include bots and other non-human users that commit to repositories."
}

function cleanup() {
  rm "$userListFile"
}

# entry point
if [ ! -f "repositories.txt" ]; then
  echo "repositories.txt file is missing" >&2
  exit 1
else
  prepare
  print_information
  process_repositories
  print_results
  cleanup
  exit 0
fi
