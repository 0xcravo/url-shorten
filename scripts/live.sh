#!/bin/sh

ls *.java | entr -scr 'make build run'
