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
