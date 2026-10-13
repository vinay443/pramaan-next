/** File extensions the bulk-upload endpoint accepts. Mirrors `BulkUploadService.DEFAULT_EXTENSIONS` (backend-java);
 *  the Bulk Upload page text and the offline mock both read this so the three cannot drift apart. */
export const BULK_UPLOAD_EXTENSIONS = [
  'cfg', 'conf', 'csv', 'doc', 'docx', 'ini', 'jpeg', 'jpg', 'json', 'log', 'md', 'pdf', 'png', 'pptx',
  'properties', 'tsv', 'txt', 'xls', 'xlsx', 'xml', 'yaml', 'yml', 'zip',
] as const
