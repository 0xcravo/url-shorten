all: build

live:
	./scripts/live.sh

build:
	javac --release 22 --enable-preview Main.java

run:
	java --enable-preview Main

clean:
	rm -rf *.class
