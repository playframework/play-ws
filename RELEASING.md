## Releasing

This project uses `sbt-release` to push to Sonatype and Maven. You will need Lightbend Sonatype credentials and a GPG key that is available on one of the public keyservers to release this project.

To release cleanly, you should clone this project fresh into a directory with writable credentials (i.e. you have ssh key to github):

```bash
mkdir releases
cd releases
git clone git@github.com:playframework/play-ws.git
```

and from there you can release:

```bash
cd play-ws
./release
```

The script will walk you through integration tests and publishing.
