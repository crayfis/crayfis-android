#/bin/bash

# awesome script to auto-update the project version
# at every build, from git revision.

echo "Auto Version: `pwd`"

CODE=`git tag | grep -c ^v[0-9]`
NAME=`git describe --dirty | sed -e 's/^v//'`
COMMITS=`echo ${NAME} | sed -e 's/[0-9\.]*//'`

if [ "x${COMMITS}x" = "xx" ] ; then
    VERSION="${NAME}"
else
    BRANCH=" (`git branch | grep "^\*" | sed -e 's/^..//'`)"
    VERSION="${NAME}${BRANCH}"
fi

echo "   Code: ${CODE}"
echo "   Ver:  ${VERSION}"

cat AndroidManifest.xml | \
    sed -e "s/android:versionCode=\"[0-9][0-9]*\"/android:versionCode=\"${CODE}\"/" \
        -e "s/android:versionName=\".*\"/android:versionName=\"${VERSION}\"/" \
    > bin/AndroidManifest.xml

exit 0
