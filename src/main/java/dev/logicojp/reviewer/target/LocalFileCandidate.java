package dev.logicojp.reviewer.target;

import java.nio.file.Path;

record LocalFileCandidate(Path path, long size) {
}
