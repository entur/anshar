#!/bin/bash

# The script take add an empty commit in a given hub repo to trigger build

set -euo pipefail

          repository="${1}"
          branch="${2}"
          git_message="${3}"
          working_dir="workdir"
          git clone --single-branch --branch "$branch" "https://github.com/$repository" "$working_dir"

          cd ${working_dir} || exit
          git config --global user.email "circleci.service@entur.org"
          git config --global user.name "entur-circleci"
          repo_name=${CIRCLE_PROJECT_REPONAME}
          git_hash=$(echo "$CIRCLE_SHA1" | cut -c1-7)
          repo_name_hash="${repo_name}(${git_hash})"

          if [[ "$git_message" == "CCI:"* ]]; then
              git_msg="$git_message -> $repo_name_hash"
          else
              git_msg="CCI:"$repo_name_hash
          fi
          echo "and git message is ${git_msg}"
          git pull
          git commit --allow-empty -m "$git_msg"
          # Push quietly to prevent showing in log
          echo "push empty commit "
          git push -q origin "$branch"
           echo "delete working direcotry"
          cd ..
          rm -rf "$working_dir"
           echo "git commit process completed."