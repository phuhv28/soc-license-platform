import axios from 'axios';

const MANAGEMENT_API_URL = import.meta.env.VITE_MANAGEMENT_API_URL || '';

const client = axios.create({
  baseURL: MANAGEMENT_API_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,
});

// Response interceptor — unwrap ApiResponse.data
client.interceptors.response.use(
  (response) => response,
  (error) => {
    const message = error.response?.data?.message || error.message || 'Unknown error';
    console.error('API Error:', message);
    return Promise.reject(new Error(message));
  }
);

export default client;
