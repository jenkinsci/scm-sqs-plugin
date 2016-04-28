#!/bin/bash
# --------------------------------------------------------------------------------
NAME=scm-sqs-plugin
echo Preparing $NAME for release

# Query user for info
read -p "Enter version number to use for the release: " VERSION_RELEASE
read -p "Enter name for the release tag [$NAME-$VERSION_RELEASE]: " TAG_RELEASE
read -p "Enter version number for next development iteration: " VERSION_SNAPSHOT

if [[ -z "$TAG_RELEASE" ]]; then
  TAG_RELEASE=$NAME-$VERSION_RELEASE
fi

if [[ "$VERSION_SNAPSHOT" != *-SNAPSHOT ]]; then
  VERSION_SNAPSHOT=$VERSION_SNAPSHOT-SNAPSHOT
fi

# Show info for review
echo
echo "Release version  : $VERSION_RELEASE"
echo "Tag release with : $TAG_RELEASE"
echo "Next iteration   : $VERSION_SNAPSHOT"
read -p "Continue (y/N)? " -s -n1 KEY
echo

if [[ "$KEY" != "y" ]]; then
	echo "Aborted"
	exit
fi

# Set the new version, commit, create a tag
mvn versions:set -DnewVersion=$VERSION_RELEASE
if [[ $? -ne 0 ]]; then
	echo
	echo "Error setting release version. Please verify and fix any errors reported above."
	echo
	exit
fi

git add pom.xml
git commit -m "prepare release $TAG_RELEASE"
git tag $TAG_RELEASE

# Deploy the new version
mvn deploy
if [[ $? -ne 0 ]]; then
	echo
	echo "Error deploying version. Please verify and fix any errors reported above."
	echo
	exit
fi

# Set the new snapshot version, commit
mvn versions:set -DnewVersion=$VERSION_SNAPSHOT
if [[ $? -ne 0 ]]; then
	echo
	echo "Error setting snapshot version. Please verify and fix any errors reported above."
	echo
	exit
fi

git add pom.xml
git commit -m "prepare for next development iteration"

# Push changes and tags
# git push
# git push --tags

# Clean up backup files
# rm pom.xml.versionsBackup

echo
echo "done."
