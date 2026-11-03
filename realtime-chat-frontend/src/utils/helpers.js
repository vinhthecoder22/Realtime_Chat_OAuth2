export const initials = (name) => (name ? name.slice(0, 2).toUpperCase() : "?");

export const avatarColor = (name) => {
  if (!name) return "av-0";
  let h = 0;
  for (let i = 0; i < name.length; i++) h = name.charCodeAt(i) + ((h << 5) - h);
  return `av-${Math.abs(h) % 8}`;
};

export const formatTs = (isoString) => {
  if (!isoString) return "";
  try {
    let dateObj;
    if (Array.isArray(isoString)) {
      dateObj = new Date(
        isoString[0],
        isoString[1] - 1,
        isoString[2],
        isoString[3],
        isoString[4],
      );
    } else {
      dateObj = new Date(isoString);
    }

    return dateObj.toLocaleTimeString("vi-VN", {
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return "";
  }
};

export const formatRelativeTime = (isoString) => {
  if (!isoString) return "";
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return "";

    const now = new Date();
    const diffMs = now - date;
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

    const isToday =
      date.getDate() === now.getDate() &&
      date.getMonth() === now.getMonth() &&
      date.getFullYear() === now.getFullYear();

    if (isToday) {
      return date.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
    }

    if (diffDays === 1) {
      return "Hôm qua";
    }

    if (diffDays < 7 && diffDays > 1) {
      const days = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];
      return days[date.getDay()];
    }

    return date.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" });
  } catch {
    return "";
  }
};

export const formatLastSeen = (isoString) => {
  if (!isoString) return "";
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return "";
    const diffSec = Math.floor((Date.now() - date.getTime()) / 1000);
    if (diffSec < 60) return "vừa xong";
    const diffMin = Math.floor(diffSec / 60);
    if (diffMin < 60) return `${diffMin} phút trước`;
    const diffHour = Math.floor(diffMin / 60);
    if (diffHour < 24) return `${diffHour} giờ trước`;
    const diffDays = Math.floor(diffHour / 24);
    if (diffDays === 1) return "hôm qua";
    if (diffDays < 7) return `${diffDays} ngày trước`;
    return date.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" });
  } catch {
    return "";
  }
};
