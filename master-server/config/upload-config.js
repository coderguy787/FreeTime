/**
 * FreeTime Upload Configuration
 * Configures multer for image uploads with validation and limits
 */

const multer = require('multer');
const path = require('path');

// Store uploaded files in memory before sending to GridFS
const storage = multer.memoryStorage();

// File filter to only accept images
const fileFilter = (req, file, cb) => {
  // Allowed image types
  const allowedMimes = [
    'image/jpeg',
    'image/png',
    'image/gif',
    'image/webp'
  ];

  if (allowedMimes.includes(file.mimetype)) {
    console.log(`[OK] Image file accepted: ${file.originalname} (${file.mimetype})`);
    cb(null, true);
  } else {
    console.error(`[ERROR] Invalid file type: ${file.mimetype}`);
    cb(new Error('Only JPEG, PNG, GIF, and WebP images are allowed'), false);
  }
};

// Create multer upload middleware
const upload = multer({
  storage: storage,
  fileFilter: fileFilter,
  limits: {
    fileSize: 5 * 1024 * 1024 // 5MB max file size
  }
});

module.exports = {
  upload,
  // File size limit in bytes (5MB)
  maxFileSize: 5 * 1024 * 1024,
  // Allowed image extensions
  allowedExtensions: ['.jpg', '.jpeg', '.png', '.gif', '.webp'],
  // Allowed MIME types
  allowedMimes: [
    'image/jpeg',
    'image/png',
    'image/gif',
    'image/webp'
  ]
};
