export const formatCurrency = (amount: number | string, currencyCode: string = 'INR'): string => {
  const numericAmount = typeof amount === 'string' ? parseFloat(amount) : amount;
  
  if (isNaN(numericAmount)) {
    return '₹0.00';
  }
  
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: currencyCode,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(numericAmount);
};

export const formatPercentage = (value: number | string): string => {
  const numericValue = typeof value === 'string' ? parseFloat(value) : value;
  
  if (isNaN(numericValue)) {
    return '0%';
  }
  
  return `${numericValue.toFixed(1)}%`;
};

export const formatNumber = (value: number | string): string => {
  const numericValue = typeof value === 'string' ? parseInt(value, 10) : value;
  
  if (isNaN(numericValue)) {
    return '0';
  }
  
  return new Intl.NumberFormat('en-IN').format(numericValue);
};

export const formatDate = (isoString: string): string => {
  try {
    const date = new Date(isoString);
    return new Intl.DateTimeFormat('en-IN', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    }).format(date);
  } catch (e) {
    return isoString;
  }
};
