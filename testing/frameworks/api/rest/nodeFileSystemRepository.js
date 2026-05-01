const fs = require('node:fs');
const path = require('node:path');
const { ExampleUsageRepository } = require('../../../core/interfaces/exampleUsageRepository');

class NodeFileSystemRepository extends ExampleUsageRepository {
  readText(filePath) {
    return fs.readFileSync(filePath, 'utf8');
  }

  exists(filePath) {
    return fs.existsSync(filePath);
  }

  isFile(filePath) {
    return this.exists(filePath) && fs.statSync(filePath).isFile();
  }

  resolveRelative(fromFilePath, targetPath) {
    return path.resolve(path.dirname(fromFilePath), targetPath);
  }

  relativeToRoot(rootPath, targetPath) {
    return path.relative(rootPath, targetPath).split(path.sep).join('/');
  }

  isWithin(rootPath, targetPath) {
    const relativePath = path.relative(rootPath, targetPath);
    return relativePath === '' || (!relativePath.startsWith('..') && !path.isAbsolute(relativePath));
  }
}

module.exports = { NodeFileSystemRepository };
