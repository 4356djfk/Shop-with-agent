"""
Spring Boot API 客户端基础类
用于与商城后端进行通信
"""
import os
import json
from typing import Optional, Dict, Any, List
from dataclasses import dataclass
import requests
from requests.exceptions import RequestException
import logging

logger = logging.getLogger(__name__)


@dataclass
class APIConfig:
    """API 配置"""
    base_url: str
    timeout: int = 30
    api_key: Optional[str] = None
    
    @classmethod
    def from_env(cls) -> "APIConfig":
        """从环境变量加载配置"""
        return cls(
            base_url=os.getenv("SPRING_BOOT_API_URL", "https://taking-distances-mines-pod.trycloudflare.com/api"),
            timeout=int(os.getenv("API_TIMEOUT", "30")),
            api_key=os.getenv("API_KEY")
        )


class SpringBootAPIClient:
    """Spring Boot API 客户端"""
    
    def __init__(self, config: Optional[APIConfig] = None):
        self.config = config or APIConfig.from_env()
        self.session = requests.Session()
        
        # 设置默认请求头
        self.session.headers.update({
            "Content-Type": "application/json",
            "Accept": "application/json"
        })
        
        # 如果有 API Key，添加到请求头
        if self.config.api_key:
            self.session.headers["Authorization"] = f"Bearer {self.config.api_key}"
    
    def _build_url(self, endpoint: str) -> str:
        """构建完整 URL"""
        endpoint = endpoint.lstrip("/")
        return f"{self.config.base_url}/{endpoint}"
    
    def _handle_response(self, response: requests.Response) -> Dict[str, Any]:
        """处理响应"""
        try:
            response.raise_for_status()
            return response.json()
        except RequestException as e:
            logger.error(f"API request failed: {e}")
            raise
        except json.JSONDecodeError as e:
            logger.error(f"Failed to decode JSON response: {e}")
            raise
    
    def get(self, endpoint: str, params: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """GET 请求"""
        url = self._build_url(endpoint)
        logger.info(f"GET {url} with params: {params}")
        
        response = self.session.get(url, params=params, timeout=self.config.timeout)
        return self._handle_response(response)
    
    def post(self, endpoint: str, data: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """POST 请求"""
        url = self._build_url(endpoint)
        logger.info(f"POST {url} with data: {data}")
        
        response = self.session.post(url, json=data, timeout=self.config.timeout)
        return self._handle_response(response)
    
    def put(self, endpoint: str, data: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """PUT 请求"""
        url = self._build_url(endpoint)
        logger.info(f"PUT {url} with data: {data}")
        
        response = self.session.put(url, json=data, timeout=self.config.timeout)
        return self._handle_response(response)
    
    def delete(self, endpoint: str) -> Dict[str, Any]:
        """DELETE 请求"""
        url = self._build_url(endpoint)
        logger.info(f"DELETE {url}")
        
        response = self.session.delete(url, timeout=self.config.timeout)
        return self._handle_response(response)


# 全局客户端实例
_client: Optional[SpringBootAPIClient] = None


def get_api_client() -> SpringBootAPIClient:
    """获取 API 客户端单例"""
    global _client
    if _client is None:
        _client = SpringBootAPIClient()
    return _client
