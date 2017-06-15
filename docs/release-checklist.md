# Release checklist

Are you going to publish a new release of compojure-api? Great! Please use this
checklist to ensure that the release is properly made. The goal is to make it
easy for both the users and the maintainers to know what's included in the
release.

* [ ] `CHANGELOG.md` contains a high-level summary of the changes in the new release.
  * [ ] Breaking changes, if any, have been highlighted.
* [ ] A JAR has been deployed to Clojars.
  * [ ] Your working tree was clean when you built the JAR.
  * [ ] The JAR is signed with a public key that has been published on the keyservers.
* [ ] The release has been tagged in git.
  * [ ] The tag has been pushed to GitHub.
  * [ ] The tag points to the same commit as the JAR on Clojars.
* [ ] The API reference has been updated by running `scripts/build-docs.sh`.
