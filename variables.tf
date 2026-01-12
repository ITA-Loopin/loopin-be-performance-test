variable "prefix" {
  description = "Prefix for all resources"
  default     = "loopin"
}

variable "region" {
  description = "region"
  default     = "ap-northeast-2"
}

variable "admin_allowed_cidrs" {
  type        = list(string)
  description = "CIDRs allowed to access NPM admin (port 81)"
  default     = []
}

variable "ssh_public_keys" {
  description = "EC2(ec2-user)에 등록할 SSH 공개키(authorized_keys)"
  type        = list(string)
  default     = []
}

variable "s3_bucket_name" {
  description = "loopin-bucket-v1"
  type        = string
  default     = "loopin-bucket-v1"
}
