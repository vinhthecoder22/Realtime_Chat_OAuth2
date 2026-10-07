import { useState, useEffect } from "react";

/**
 * Custom hook để debounce một giá trị bất kỳ
 * @param {any} value - Giá trị cần debounce (string, object,...)
 * @param {number} delay - Thời gian chờ (milliseconds)
 * @returns {any} Giá trị sau khi đã được debounce
 */
export const useDebounce = (value, delay) => {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    // Đặt một timer để update giá trị sau khoảng thời gian delay
    const handler = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    // Cleanup function: Hủy timer cũ nếu value thay đổi trước khi timer chạy xong
    return () => {
      clearTimeout(handler);
    };
  }, [value, delay]); // Chỉ re-run effect nếu value hoặc delay thay đổi

  return debouncedValue;
};
