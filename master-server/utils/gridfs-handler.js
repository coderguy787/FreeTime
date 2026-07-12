/**
 * FreeTime GridFS Handler
 * Handles file uploads/downloads for group pictures using MongoDB GridFS
 */

const { GridFSBucket, ObjectId } = require('mongodb');

class GridFSHandler {
  constructor(dbConnection) {
    this.db = dbConnection;
    this.bucket = new GridFSBucket(dbConnection);
    console.log('[OK] GridFS handler initialized');
  }

  /**
   * Upload file to GridFS
   */
  async uploadFile(fileBuffer, filename, metadata = {}) {
    return new Promise((resolve, reject) => {
      try {
        const uploadStream = this.bucket.openUploadStream(filename, {
          metadata: {
            ...metadata,
            uploadedAt: new Date()
          }
        });

        uploadStream.on('finish', (file) => {
          console.log(`[OK] File uploaded to GridFS: ${file._id} (${filename}, ${file.length} bytes)`);
          resolve({
            fileId: file._id,
            filename: file.filename,
            size: file.length,
            uploadedAt: file.uploadDate
          });
        });

        uploadStream.on('error', (err) => {
          console.error(`[ERROR] GridFS upload failed: ${err.message}`);
          reject(err);
        });

        uploadStream.write(fileBuffer);
        uploadStream.end();
      } catch (err) {
        console.error(`[ERROR] Failed to start upload: ${err.message}`);
        reject(err);
      }
    });
  }

  /**
   * Download file from GridFS
   */
  async downloadFile(fileId) {
    return new Promise((resolve, reject) => {
      try {
        const objectId = new ObjectId(fileId);
        const downloadStream = this.bucket.openDownloadStream(objectId);

        let chunks = [];
        downloadStream.on('data', (chunk) => {
          chunks.push(chunk);
        });

        downloadStream.on('end', () => {
          const buffer = Buffer.concat(chunks);
          console.log(`[OK] File downloaded from GridFS: ${fileId} (${buffer.length} bytes)`);
          resolve(buffer);
        });

        downloadStream.on('error', (err) => {
          console.error(`[ERROR] GridFS download failed: ${err.message}`);
          reject(err);
        });
      } catch (err) {
        console.error(`[ERROR] Failed to download file: ${err.message}`);
        reject(err);
      }
    });
  }

  /**
   * Delete file from GridFS
   */
  async deleteFile(fileId) {
    try {
      const objectId = new ObjectId(fileId);
      await this.bucket.delete(objectId);
      console.log(`[OK] File deleted from GridFS: ${fileId}`);
      return true;
    } catch (err) {
      console.error(`[ERROR] Failed to delete file: ${err.message}`);
      return false;
    }
  }

  /**
   * Get file info from GridFS
   */
  async getFileInfo(fileId) {
    try {
      const objectId = new ObjectId(fileId);
      const files = await this.db.collection('fs.files').findOne({ _id: objectId });
      
      if (!files) {
        return null;
      }

      return {
        fileId: files._id,
        filename: files.filename,
        size: files.length,
        uploadedAt: files.uploadDate,
        metadata: files.metadata
      };
    } catch (err) {
      console.error(`[ERROR] Failed to get file info: ${err.message}`);
      return null;
    }
  }
}

module.exports = GridFSHandler;
