import axios from 'axios';
import keycloak from '../keycloak';

const MANAGEMENT_API_URL = import.meta.env.VITE_MANAGEMENT_API_URL || '';

const client = axios.create({
  baseURL: MANAGEMENT_API_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
});

// Request interceptor — attach token
client.interceptors.request.use((config) => {
  if (keycloak.token) {
    config.headers.Authorization = `Bearer ${keycloak.token}`;
  }
  return config;
});

// Response interceptor — unwrap ApiResponse.data
client.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      keycloak.login();
    }
    const message = error.response?.data?.message || error.message || 'Unknown error';
    console.error('API Error:', message);
    return Promise.reject(new Error(message));
  }
);

export default client;
