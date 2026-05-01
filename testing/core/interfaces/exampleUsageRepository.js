class ExampleUsageRepository {
  readText(_filePath) {
    throw new Error('readText must be implemented by a concrete repository');
  }

  exists(_filePath) {
    throw new Error('exists must be implemented by a concrete repository');
  }

  isFile(_filePath) {
    throw new Error('isFile must be implemented by a concrete repository');
  }

  resolveRelative(_fromFilePath, _targetPath) {
    throw new Error('resolveRelative must be implemented by a concrete repository');
  }

  relativeToRoot(_rootPath, _targetPath) {
    throw new Error('relativeToRoot must be implemented by a concrete repository');
  }

  isWithin(_rootPath, _targetPath) {
    throw new Error('isWithin must be implemented by a concrete repository');
  }
}

module.exports = { ExampleUsageRepository };
